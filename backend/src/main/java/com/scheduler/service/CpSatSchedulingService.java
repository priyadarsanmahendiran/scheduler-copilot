package com.scheduler.service;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.scheduler.model.Job;
import com.scheduler.model.Schedule;
import com.scheduler.model.ScheduleMetrics;
import com.scheduler.model.ScheduleWithMetrics;
import com.scheduler.store.InMemoryStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class CpSatSchedulingService {
    private static final Logger log = LoggerFactory.getLogger(CpSatSchedulingService.class);
    private static final AtomicBoolean nativeLoaded = new AtomicBoolean(false);

    private final InMemoryStore store;

    public CpSatSchedulingService(InMemoryStore store) {
        this.store = store;
    }

    @PostConstruct
    public void loadNativeLibraries() {
        if (nativeLoaded.compareAndSet(false, true)) {
            try {
                Loader.loadNativeLibraries();
                log.info("OR-Tools native libraries loaded");
            } catch (Exception e) {
                log.error("Failed to load OR-Tools native libraries: {}", e.getMessage());
                nativeLoaded.set(false);
            }
        }
    }

    public ScheduleWithMetrics solveTimeOptimal(String triggeringMachineId) {
        SolverInputs inputs = buildInputs(triggeringMachineId);
        if (inputs.jobs.isEmpty()) {
            return emptyResult(inputs);
        }

        CpModel model = new CpModel();
        int J = inputs.jobs.size();
        int M = inputs.machines.size();

        BoolVar[][] x = new BoolVar[J][M];
        for (int j = 0; j < J; j++) {
            for (int m = 0; m < M; m++) {
                x[j][m] = model.newBoolVar("x_" + j + "_" + m);
            }
            model.addExactlyOne(Arrays.asList(x[j]));
        }

        int maxDuration = inputs.jobs.stream().mapToInt(Job::getDuration).sum()
                + inputs.existingLoads.values().stream().mapToInt(Integer::intValue).sum();

        IntVar[] loads = new IntVar[M];
        for (int m = 0; m < M; m++) {
            LinearExprBuilder lb = LinearExpr.newBuilder();
            lb.add(inputs.existingLoads.getOrDefault(inputs.machines.get(m), 0));
            for (int j = 0; j < J; j++) {
                lb.addTerm(x[j][m], inputs.jobs.get(j).getDuration());
            }
            loads[m] = model.newIntVar(0, maxDuration, "load_" + m);
            model.addEquality(loads[m], lb);
        }

        IntVar makespan = model.newIntVar(0, maxDuration, "makespan");
        model.addMaxEquality(makespan, loads);
        model.minimize(makespan);

        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            return extractResult(solver, x, inputs, (int) solver.objectiveValue());
        }
        log.warn("CP-SAT time-optimal solver returned {}, falling back to round-robin", status);
        return fallback(inputs);
    }

    public ScheduleWithMetrics solveCostOptimal(String triggeringMachineId) {
        SolverInputs inputs = buildInputs(triggeringMachineId);
        if (inputs.jobs.isEmpty()) {
            return emptyResult(inputs);
        }

        CpModel model = new CpModel();
        int J = inputs.jobs.size();
        int M = inputs.machines.size();

        BoolVar[][] x = new BoolVar[J][M];
        for (int j = 0; j < J; j++) {
            for (int m = 0; m < M; m++) {
                x[j][m] = model.newBoolVar("x_" + j + "_" + m);
            }
            model.addExactlyOne(Arrays.asList(x[j]));
        }

        // y[m] = 1 if any new job is assigned to machine m (disrupted machine)
        BoolVar[] y = new BoolVar[M];
        for (int m = 0; m < M; m++) {
            y[m] = model.newBoolVar("y_" + m);
            BoolVar[] column = new BoolVar[J];
            for (int j = 0; j < J; j++) {
                column[j] = x[j][m];
            }
            model.addMaxEquality(y[m], Arrays.asList(column));
        }

        model.minimize(LinearExpr.sum(y));

        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            int disrupted = 0;
            for (int m = 0; m < M; m++) {
                if (solver.booleanValue(y[m])) disrupted++;
            }
            int makespan = computeMakespan(solver, x, inputs);
            return extractResultWithMakespan(solver, x, inputs, makespan, disrupted);
        }
        log.warn("CP-SAT cost-optimal solver returned {}, falling back", status);
        return fallback(inputs);
    }

    /**
     * Builds solver inputs that account for ALL currently-down machines, not just the triggering one.
     * Jobs from every down machine are collected for rescheduling. The available machines list and
     * existingAssignments contain only currently-RUNNING machines, so the solver can never assign
     * a job to a machine that is already down.
     */
    private SolverInputs buildInputs(String triggeringMachineId) {
        Schedule current = store.getCurrentSchedule();
        Map<String, List<Job>> assignments = current != null ? current.getAssignments() : Map.of();

        // All down machines: the triggering failure + any others already marked DOWN
        Set<String> downMachineIds = store.getMachines().values().stream()
                .filter(m -> "DOWN".equals(m.getStatus()) || triggeringMachineId.equals(m.getId()))
                .map(m -> m.getId())
                .collect(Collectors.toSet());

        log.info("Rescheduling with down machines: {}", downMachineIds);

        // Jobs to reschedule = union of all jobs on any down machine
        List<Job> jobsToReschedule = downMachineIds.stream()
                .flatMap(mid -> assignments.getOrDefault(mid, List.of()).stream())
                .collect(Collectors.toList());

        // Available machines = RUNNING machines only, none of which are in downMachineIds
        List<String> availableMachines = store.getMachines().values().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()) && !downMachineIds.contains(m.getId()))
                .map(m -> m.getId())
                .sorted()
                .collect(Collectors.toList());

        // existingAssignments and existingLoads cover only running machines
        Map<String, Integer> existingLoads = new HashMap<>();
        Map<String, List<Job>> runningAssignments = new LinkedHashMap<>();
        for (String machineId : availableMachines) {
            List<Job> currentJobs = assignments.getOrDefault(machineId, List.of());
            existingLoads.put(machineId, currentJobs.stream().mapToInt(Job::getDuration).sum());
            runningAssignments.put(machineId, new ArrayList<>(currentJobs));
        }

        return new SolverInputs(jobsToReschedule, availableMachines, existingLoads, runningAssignments);
    }

    private ScheduleWithMetrics extractResult(CpSolver solver, BoolVar[][] x,
                                               SolverInputs inputs, int makespan) {
        return extractResultWithMakespan(solver, x, inputs, makespan, -1);
    }

    private ScheduleWithMetrics extractResultWithMakespan(CpSolver solver, BoolVar[][] x,
                                                           SolverInputs inputs, int makespan,
                                                           int disrupted) {
        // existingAssignments already contains only running machines — no need to strip down machines
        Map<String, List<Job>> newAssignments = new LinkedHashMap<>();
        Map<String, Integer> finalLoads = new HashMap<>();

        for (int m = 0; m < inputs.machines.size(); m++) {
            String machineId = inputs.machines.get(m);
            List<Job> machineJobs = new ArrayList<>(
                    inputs.existingAssignments.getOrDefault(machineId, List.of()));
            for (int j = 0; j < inputs.jobs.size(); j++) {
                if (solver.booleanValue(x[j][m])) {
                    machineJobs.add(inputs.jobs.get(j));
                }
            }
            if (!machineJobs.isEmpty()) {
                newAssignments.put(machineId, machineJobs);
            }
            finalLoads.put(machineId, machineJobs.stream().mapToInt(Job::getDuration).sum());
        }

        if (disrupted < 0) {
            disrupted = (int) finalLoads.entrySet().stream()
                    .filter(e -> inputs.existingLoads.getOrDefault(e.getKey(), 0) < e.getValue())
                    .count();
        }

        return new ScheduleWithMetrics(
                new Schedule(newAssignments),
                new ScheduleMetrics(makespan, disrupted, finalLoads));
    }

    private int computeMakespan(CpSolver solver, BoolVar[][] x, SolverInputs inputs) {
        int max = 0;
        for (int m = 0; m < inputs.machines.size(); m++) {
            String machineId = inputs.machines.get(m);
            int load = inputs.existingLoads.getOrDefault(machineId, 0);
            for (int j = 0; j < inputs.jobs.size(); j++) {
                if (solver.booleanValue(x[j][m])) {
                    load += inputs.jobs.get(j).getDuration();
                }
            }
            if (load > max) max = load;
        }
        return max;
    }

    private ScheduleWithMetrics emptyResult(SolverInputs inputs) {
        Map<String, Integer> loads = new HashMap<>(inputs.existingLoads);
        int makespan = loads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new ScheduleWithMetrics(
                new Schedule(new LinkedHashMap<>(inputs.existingAssignments)),
                new ScheduleMetrics(makespan, 0, loads));
    }

    private ScheduleWithMetrics fallback(SolverInputs inputs) {
        if (inputs.machines.isEmpty()) {
            return emptyResult(inputs);
        }
        Map<String, List<Job>> assignments = new LinkedHashMap<>(inputs.existingAssignments);
        int idx = 0;
        for (Job job : inputs.jobs) {
            String target = inputs.machines.get(idx % inputs.machines.size());
            assignments.computeIfAbsent(target, k -> new ArrayList<>()).add(job);
            idx++;
        }
        Map<String, Integer> loads = new HashMap<>();
        for (String m : inputs.machines) {
            loads.put(m, assignments.getOrDefault(m, List.of()).stream()
                    .mapToInt(Job::getDuration).sum());
        }
        int makespan = loads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new ScheduleWithMetrics(
                new Schedule(assignments),
                new ScheduleMetrics(makespan, inputs.machines.size(), loads));
    }

    private static class SolverInputs {
        final List<Job> jobs;
        final List<String> machines;
        final Map<String, Integer> existingLoads;
        final Map<String, List<Job>> existingAssignments;

        SolverInputs(List<Job> jobs, List<String> machines,
                     Map<String, Integer> existingLoads,
                     Map<String, List<Job>> existingAssignments) {
            this.jobs = jobs;
            this.machines = machines;
            this.existingLoads = existingLoads;
            this.existingAssignments = existingAssignments;
        }
    }
}
