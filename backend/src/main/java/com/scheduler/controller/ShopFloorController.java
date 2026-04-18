package com.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.service.MachineService;
import com.scheduler.service.SseBroadcaster;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class ShopFloorController {
    private static final Logger log = LoggerFactory.getLogger(ShopFloorController.class);

    private final MachineService machineService;
    private final InMemoryStore store;
    private final SseBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public ShopFloorController(MachineService machineService, InMemoryStore store,
                                SseBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.machineService = machineService;
        this.store = store;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/machines")
    public List<Machine> getMachines() {
        return machineService.getAllMachines();
    }

    @GetMapping("/schedule")
    public Schedule getSchedule() {
        return store.getCurrentSchedule();
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = broadcaster.subscribe();
        try {
            emitter.send(SseEmitter.event().name("machines")
                    .data(objectMapper.writeValueAsString(machineService.getAllMachines())));
            Schedule schedule = store.getCurrentSchedule();
            if (schedule != null) {
                emitter.send(SseEmitter.event().name("schedule")
                        .data(objectMapper.writeValueAsString(schedule)));
            }
            emitter.send(SseEmitter.event().name("pending")
                    .data(objectMapper.writeValueAsString(store.getPendingSessionByMachine())));
        } catch (Exception e) {
            log.warn("Failed to send initial SSE snapshot: {}", e.getMessage());
            emitter.completeWithError(e);
        }
        log.info("SSE client subscribed (total: approx active connections)");
        return emitter;
    }
}
