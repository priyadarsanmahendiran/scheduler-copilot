package com.scheduler.service;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchedulingService {
    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final InMemoryStore store;

    public SchedulingService(InMemoryStore store) {
        this.store = store;
    }

    // Used by OptimizationService for automatic rescheduling
    public Schedule generateNewSchedule(String failedMachineId) {
        return scheduleTimeOptimal(failedMachineId);
    }

    // Distributes affected jobs evenly across all running machines (minimises max latency)
    public Schedule scheduleTimeOptimal(String failedMachineId) {
        Map<String, List<Job>> current = store.getCurrentSchedule().getAssignments();
        List<Job> affectedJobs = current.getOrDefault(failedMachineId, List.of());

        List<Machine> runningMachines = store.getMachines().values().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()))
                .toList();

        Map<String, List<Job>> newAssignments = copyWithout(current, failedMachineId, runningMachines);

        for (int i = 0; i < affectedJobs.size(); i++) {
            Job job = affectedJobs.get(i);
            Machine target = runningMachines.get(i % runningMachines.size());
            newAssignments.get(target.getId()).add(job);
        }

        log.info("[TIME-OPTIMAL] Schedule after {} failure: {}", failedMachineId, summarise(newAssignments));
        return new Schedule(newAssignments);
    }

    // Concentrates affected jobs on the single least-loaded running machine (minimises disruption breadth)
    public Schedule scheduleCostOptimal(String failedMachineId) {
        Map<String, List<Job>> current = store.getCurrentSchedule().getAssignments();
        List<Job> affectedJobs = current.getOrDefault(failedMachineId, List.of());

        List<Machine> runningMachines = store.getMachines().values().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()))
                .toList();

        Map<String, List<Job>> newAssignments = copyWithout(current, failedMachineId, runningMachines);

        if (!runningMachines.isEmpty() && !affectedJobs.isEmpty()) {
            Machine target = runningMachines.stream()
                    .min(Comparator.comparingInt(m ->
                            newAssignments.getOrDefault(m.getId(), List.of()).stream()
                                    .mapToInt(Job::getDuration).sum()))
                    .orElse(runningMachines.get(0));

            newAssignments.get(target.getId()).addAll(affectedJobs);
        }

        log.info("[COST-OPTIMAL] Schedule after {} failure: {}", failedMachineId, summarise(newAssignments));
        return new Schedule(newAssignments);
    }

    private Map<String, List<Job>> copyWithout(Map<String, List<Job>> current,
                                                String excludeId,
                                                List<Machine> runningMachines) {
        Map<String, List<Job>> result = new HashMap<>();
        for (Map.Entry<String, List<Job>> entry : current.entrySet()) {
            if (!entry.getKey().equals(excludeId)) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (Machine m : runningMachines) {
            result.putIfAbsent(m.getId(), new ArrayList<>());
        }
        return result;
    }

    private String summarise(Map<String, List<Job>> assignments) {
        return assignments.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().stream().map(Job::getId).toList())
                .toList()
                .toString();
    }
}
