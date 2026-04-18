package com.scheduler.controller;

import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.service.MachineService;
import com.scheduler.store.InMemoryStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShopFloorController {
    private final MachineService machineService;
    private final InMemoryStore store;

    public ShopFloorController(MachineService machineService, InMemoryStore store) {
        this.machineService = machineService;
        this.store = store;
    }

    @GetMapping("/machines")
    public List<Machine> getMachines() {
        return machineService.getAllMachines();
    }

    @GetMapping("/schedule")
    public Schedule getSchedule() {
        return store.getCurrentSchedule();
    }
}
