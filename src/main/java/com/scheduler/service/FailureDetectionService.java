package com.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FailureDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FailureDetectionService.class);
    private static final long HEARTBEAT_TIMEOUT_MS = 5000;

    private final MachineService machineService;
    private final OptimizationService optimizationService;

    public FailureDetectionService(MachineService machineService, OptimizationService optimizationService) {
        this.machineService = machineService;
        this.optimizationService = optimizationService;
    }

    @Scheduled(fixedRate = 3000)
    public void detectFailures() {
        long now = System.currentTimeMillis();
        machineService.getAllMachines().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()))
                .filter(m -> (now - m.getLastHeartbeat()) > HEARTBEAT_TIMEOUT_MS)
                .forEach(m -> {
                    machineService.updateMachineStatus(m.getId(), "DOWN");
                    log.warn("Machine {} marked as DOWN (last heartbeat {}ms ago)", m.getId(), now - m.getLastHeartbeat());
                    optimizationService.handleMachineFailure(m);
                });
    }
}
