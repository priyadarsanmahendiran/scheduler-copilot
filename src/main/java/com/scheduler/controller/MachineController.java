package com.scheduler.controller;

import com.scheduler.service.MachineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/machines")
public class MachineController {
    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @PostMapping("/{id}/down")
    public ResponseEntity<String> markMachineAsDown(@PathVariable String id) {
        if (machineService.getMachineById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        machineService.markAsDown(id);
        return ResponseEntity.ok("Machine " + id + " marked as DOWN");
    }
}
