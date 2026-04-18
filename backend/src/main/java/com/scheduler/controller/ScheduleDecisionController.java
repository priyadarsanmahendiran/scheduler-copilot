package com.scheduler.controller;

import com.scheduler.model.ChatRequest;
import com.scheduler.model.ChatResponse;
import com.scheduler.model.ChoiceResponse;
import com.scheduler.model.MachineFailureRequest;
import com.scheduler.model.ScheduleDecisionResponse;
import com.scheduler.model.UserChoiceRequest;
import com.scheduler.service.ScheduleDecisionService;
import com.scheduler.store.InMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleDecisionController {
    private final ScheduleDecisionService scheduleDecisionService;
    private final InMemoryStore store;

    public ScheduleDecisionController(ScheduleDecisionService scheduleDecisionService,
                                      InMemoryStore store) {
        this.scheduleDecisionService = scheduleDecisionService;
        this.store = store;
    }

    @PostMapping("/failure")
    public ResponseEntity<ScheduleDecisionResponse> processFailure(
            @RequestBody MachineFailureRequest request) {
        if (request.getMachineId() == null || request.getMachineId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(scheduleDecisionService.processFailure(request.getMachineId()));
    }

    @PostMapping("/choose")
    public ResponseEntity<ChoiceResponse> processChoice(@RequestBody UserChoiceRequest request) {
        if (request.getSessionId() == null || request.getUserMessage() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                scheduleDecisionService.processChoice(request.getSessionId(), request.getUserMessage()));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getSessionId() == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return scheduleDecisionService.chat(request.getSessionId(), request.getMessage())
                .map(reply -> ResponseEntity.ok(new ChatResponse(reply)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ScheduleDecisionResponse> getSession(@PathVariable String sessionId) {
        return scheduleDecisionService.getSessionResponse(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recovery")
    public ResponseEntity<ScheduleDecisionResponse> processRecovery(
            @RequestBody MachineFailureRequest request) {
        if (request.getMachineId() == null || request.getMachineId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(scheduleDecisionService.processRecovery(request.getMachineId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<Map<String, String>> getPendingSessions() {
        return ResponseEntity.ok(store.getPendingSessionByMachine());
    }
}
