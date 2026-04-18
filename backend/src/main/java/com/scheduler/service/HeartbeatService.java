package com.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeartbeatService {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final MachineService machineService;
    private final SseBroadcaster broadcaster;
    // Tracks whether a degraded machine skipped its previous tick.
    // A skip is only allowed when the previous tick was NOT skipped, so consecutive
    // skips are impossible. Max gap = 2 ticks × 2000ms = 4000ms < 5000ms timeout.
    private final ConcurrentHashMap<String, Boolean> lastSkipped = new ConcurrentHashMap<>();

    public HeartbeatService(MachineService machineService, SseBroadcaster broadcaster) {
        this.machineService = machineService;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedRate = 2000)
    public void updateHeartbeats() {
        long now = System.currentTimeMillis();
        machineService.getAllMachines().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()) && !m.isHeartbeatBlocked())
                .forEach(m -> {
                    if (m.isDegraded()) {
                        boolean wasSkipped = lastSkipped.getOrDefault(m.getId(), false);
                        if (!wasSkipped && Math.random() < 0.45) {
                            lastSkipped.put(m.getId(), true);
                            return; // skip this tick only — next tick is guaranteed
                        }
                    }
                    lastSkipped.remove(m.getId());
                    machineService.updateHeartbeat(m.getId(), now);
                });
        broadcaster.broadcast("machines", machineService.getAllMachines());
        log.debug("Heartbeat tick");
    }
}
