package com.scheduler.store;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.service.SseBroadcaster;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class InMemoryStore {
    private final Map<String, Machine> machines = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private volatile Schedule currentSchedule;
    private final Map<String, String> pendingSessionByMachine = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> heartbeatIntervals = new ConcurrentHashMap<>();

    private final SseBroadcaster broadcaster;

    public InMemoryStore(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    public Map<String, Machine> getMachines() { return machines; }
    public Map<String, Job> getJobs() { return jobs; }

    public Schedule getCurrentSchedule() { return currentSchedule; }
    public void setCurrentSchedule(Schedule schedule) {
        this.currentSchedule = schedule;
        broadcaster.broadcast("schedule", schedule);
    }

    public void setPendingSession(String machineId, String sessionId) {
        pendingSessionByMachine.put(machineId, sessionId);
        broadcaster.broadcast("pending", Map.copyOf(pendingSessionByMachine));
    }

    public String getPendingSession(String machineId) {
        return pendingSessionByMachine.get(machineId);
    }

    public void clearPendingSession(String machineId) {
        pendingSessionByMachine.remove(machineId);
        broadcaster.broadcast("pending", Map.copyOf(pendingSessionByMachine));
    }

    public Map<String, String> getPendingSessionByMachine() {
        return Map.copyOf(pendingSessionByMachine);
    }

    public void recordHeartbeatInterval(String machineId, long intervalMs) {
        Deque<Long> deque = heartbeatIntervals.computeIfAbsent(machineId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(intervalMs);
            while (deque.size() > 12) deque.pollFirst();
        }
    }

    public List<Long> getHeartbeatIntervals(String machineId) {
        Deque<Long> deque = heartbeatIntervals.get(machineId);
        if (deque == null) return List.of();
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    public void clearHeartbeatIntervals(String machineId) {
        heartbeatIntervals.remove(machineId);
    }
}
