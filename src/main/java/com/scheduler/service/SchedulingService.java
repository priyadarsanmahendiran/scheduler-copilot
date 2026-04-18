package com.scheduler.service;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public Schedule generateNewSchedule(String failedMachineId) {
        Map<String, List<Job>> current = store.getCurrentSchedule().getAssignments();

        List<Job> affectedJobs = current.getOrDefault(failedMachineId, List.of());
        log.info("Affected jobs from failed machine {}: {}", failedMachineId,
                affectedJobs.stream().map(Job::getId).toList());

        List<Machine> runningMachines = store.getMachines().values().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()))
                .toList();

        // copy existing assignments, excluding the failed machine
        Map<String, List<Job>> newAssignments = new HashMap<>();
        for (Map.Entry<String, List<Job>> entry : current.entrySet()) {
            if (!entry.getKey().equals(failedMachineId)) {
                newAssignments.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        // ensure all running machines have an entry
        for (Machine m : runningMachines) {
            newAssignments.putIfAbsent(m.getId(), new ArrayList<>());
        }

        // round-robin distribute affected jobs across running machines
        for (int i = 0; i < affectedJobs.size(); i++) {
            Job job = affectedJobs.get(i);
            Machine target = runningMachines.get(i % runningMachines.size());
            newAssignments.get(target.getId()).add(job);
            log.info("Job {} reassigned to machine {}", job.getId(), target.getId());
        }

        log.info("New schedule: {}", newAssignments.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().stream().map(Job::getId).toList())
                .toList());

        return new Schedule(newAssignments);
    }
}
