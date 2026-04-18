package com.scheduler.service;

import com.scheduler.model.Machine;
import com.scheduler.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MachineService {
    private final InMemoryStore store;
    private final SseBroadcaster broadcaster;

    public MachineService(InMemoryStore store, SseBroadcaster broadcaster) {
        this.store = store;
        this.broadcaster = broadcaster;
    }

    public List<Machine> getAllMachines() {
        return List.copyOf(store.getMachines().values());
    }

    public Machine getMachineById(String id) {
        return store.getMachines().get(id);
    }

    public void updateMachineStatus(String id, String status) {
        Machine machine = store.getMachines().get(id);
        if (machine != null) {
            machine.setStatus(status);
        }
    }

    public void updateHeartbeat(String id, long timestamp) {
        Machine machine = store.getMachines().get(id);
        if (machine != null) {
            long prev = machine.getLastHeartbeat();
            if (prev > 0) {
                store.recordHeartbeatInterval(id, timestamp - prev);
            }
            machine.setLastHeartbeat(timestamp);
        }
    }

    public void markAsDown(String id) {
        Machine machine = store.getMachines().get(id);
        if (machine != null) {
            machine.setHeartbeatBlocked(true);
            machine.setRiskLevel(null);
            machine.setRiskReason(null);
            store.clearHeartbeatIntervals(id);
            broadcaster.broadcast("machines", getAllMachines());
        }
    }

    public void markAsDegraded(String id) {
        Machine machine = store.getMachines().get(id);
        if (machine != null) {
            machine.setDegraded(true);
            broadcaster.broadcast("machines", getAllMachines());
        }
    }
}
