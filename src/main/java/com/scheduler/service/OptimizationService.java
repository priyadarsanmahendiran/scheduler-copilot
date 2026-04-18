package com.scheduler.service;

import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OptimizationService {
    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);

    private final SchedulingService schedulingService;
    private final InMemoryStore store;

    public OptimizationService(SchedulingService schedulingService, InMemoryStore store) {
        this.schedulingService = schedulingService;
        this.store = store;
    }

    public void handleMachineFailure(Machine machine) {
        log.info("Optimization triggered for machine {}", machine.getId());
        Schedule newSchedule = schedulingService.generateNewSchedule(machine.getId());
        store.setCurrentSchedule(newSchedule);
        log.info("Schedule updated after failure of machine {}", machine.getId());
    }
}
