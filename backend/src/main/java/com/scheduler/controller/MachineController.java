package com.scheduler.controller;

import com.scheduler.service.MachineService;
import com.scheduler.service.OptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/machines")
public class MachineController {
    private final MachineService machineService;
    private final OptimizationService optimizationService;

    public MachineController(MachineService machineService, OptimizationService optimizationService) {
        this.machineService = machineService;
        this.optimizationService = optimizationService;
    }

    @PostMapping("/{id}/down")
    public ResponseEntity<String> markMachineAsDown(@PathVariable String id) {
        if (machineService.getMachineById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        machineService.markAsDown(id);
        return ResponseEntity.ok("Machine " + id + " marked as DOWN");
    }

    @PostMapping("/{id}/degrade")
    public ResponseEntity<String> simulateDegradation(@PathVariable String id) {
        if (machineService.getMachineById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        machineService.markAsDegraded(id);
        return ResponseEntity.ok("Machine " + id + " degradation simulation started");
    }

    @PostMapping("/{id}/recover")
    public ResponseEntity<String> recoverMachine(@PathVariable String id) {
        if (machineService.getMachineById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        machineService.markAsRecovered(id);
        return ResponseEntity.ok("Machine " + id + " marked as RUNNING");
    }

    /**
     * Real heartbeat ingestion endpoint — machine hardware POSTs here on every tick.
     * Detects DOWN→RUNNING self-recovery and triggers async recovery analysis.
     */
    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<String> receiveHeartbeat(@PathVariable String id) {
        if (machineService.getMachineById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        boolean selfRecovered = machineService.handleIncomingHeartbeat(id);
        if (selfRecovered) {
            optimizationService.handleMachineRecovery(id);
            return ResponseEntity.ok("Machine " + id + " self-recovered — recovery analysis started");
        }
        return ResponseEntity.ok("Heartbeat recorded for " + id);
    }

    /**
     * Simulation shortcut — mimics the hardware resuming heartbeats without needing real PLCs.
     */
    @PostMapping("/{id}/self-recover")
    public ResponseEntity<String> simulateSelfRecovery(@PathVariable String id) {
        return receiveHeartbeat(id);
    }
}
