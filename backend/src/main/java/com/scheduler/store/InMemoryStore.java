package com.scheduler.store;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class InMemoryStore {
    private final Map<String, Machine> machines = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private volatile Schedule currentSchedule;
    private final Map<String, String> pendingSessionByMachine = new ConcurrentHashMap<>();

    public Map<String, Machine> getMachines() { return machines; }
    public Map<String, Job> getJobs() { return jobs; }

    public Schedule getCurrentSchedule() { return currentSchedule; }
    public void setCurrentSchedule(Schedule schedule) { this.currentSchedule = schedule; }

    public void setPendingSession(String machineId, String sessionId) {
        pendingSessionByMachine.put(machineId, sessionId);
    }

    public String getPendingSession(String machineId) {
        return pendingSessionByMachine.get(machineId);
    }

    public void clearPendingSession(String machineId) {
        pendingSessionByMachine.remove(machineId);
    }

    public Map<String, String> getPendingSessionByMachine() {
        return Map.copyOf(pendingSessionByMachine);
    }
}
