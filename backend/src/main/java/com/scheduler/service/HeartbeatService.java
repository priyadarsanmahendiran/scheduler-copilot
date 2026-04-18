package com.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatService {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final MachineService machineService;

    public HeartbeatService(MachineService machineService) {
        this.machineService = machineService;
    }

    @Scheduled(fixedRate = 2000)
    public void updateHeartbeats() {
        long now = System.currentTimeMillis();
        machineService.getAllMachines().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()) && !m.isHeartbeatBlocked())
                .forEach(m -> machineService.updateHeartbeat(m.getId(), now));
        log.info("Heartbeat updated for all RUNNING machines");
    }
}
