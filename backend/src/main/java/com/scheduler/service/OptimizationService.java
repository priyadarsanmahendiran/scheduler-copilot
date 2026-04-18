package com.scheduler.service;

import com.scheduler.model.Machine;
import com.scheduler.model.ScheduleDecisionResponse;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OptimizationService {
    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);

    private final ScheduleDecisionService scheduleDecisionService;
    private final InMemoryStore store;

    public OptimizationService(ScheduleDecisionService scheduleDecisionService, InMemoryStore store) {
        this.scheduleDecisionService = scheduleDecisionService;
        this.store = store;
    }

    @Async
    public void handleMachineFailure(Machine machine) {
        String machineId = machine.getId();
        log.info("Optimization triggered for machine {}", machineId);

        String existing = store.getPendingSession(machineId);
        if (existing != null) {
            log.info("Pending session {} already exists for machine {}, skipping", existing, machineId);
            return;
        }

        try {
            ScheduleDecisionResponse response = scheduleDecisionService.processFailure(machineId);
            log.info("Decision session {} created for machine {}. Awaiting operator choice.",
                    response.getSessionId(), machineId);
        } catch (Exception e) {
            log.error("Failed to process failure for machine {}: {}", machineId, e.getMessage(), e);
        }
    }

    @Async
    public void handleMachineRecovery(String machineId) {
        log.info("Recovery optimization triggered for machine {}", machineId);

        String existing = store.getPendingSession(machineId);
        if (existing != null) {
            log.info("Pending session {} already exists for machine {}, skipping recovery", existing, machineId);
            return;
        }

        try {
            ScheduleDecisionResponse response = scheduleDecisionService.processRecovery(machineId);
            log.info("Recovery session {} created for machine {}. Awaiting operator choice.",
                    response.getSessionId(), machineId);
        } catch (Exception e) {
            log.error("Failed to process recovery for machine {}: {}", machineId, e.getMessage(), e);
        }
    }
}
