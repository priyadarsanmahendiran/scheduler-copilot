package com.scheduler.service;

import com.scheduler.model.ChoiceResponse;
import com.scheduler.model.ChoiceResult;
import com.scheduler.model.DecisionSession;
import com.scheduler.model.Schedule;
import com.scheduler.model.ScheduleDecisionResponse;
import com.scheduler.model.SchedulePair;
import com.scheduler.model.ScheduleWithMetrics;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleDecisionService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleDecisionService.class);
    private static final long SESSION_TTL_MS = 300_000;

    private final ConcurrentHashMap<String, DecisionSession> sessionCache = new ConcurrentHashMap<>();

    private final CpSatSchedulingService cpSatSchedulingService;
    private final ClaudeAgentService claudeAgentService;
    private final ChoiceInterpreterService choiceInterpreterService;
    private final InMemoryStore store;

    public ScheduleDecisionService(CpSatSchedulingService cpSatSchedulingService,
                                   ClaudeAgentService claudeAgentService,
                                   ChoiceInterpreterService choiceInterpreterService,
                                   InMemoryStore store) {
        this.cpSatSchedulingService = cpSatSchedulingService;
        this.claudeAgentService = claudeAgentService;
        this.choiceInterpreterService = choiceInterpreterService;
        this.store = store;
    }

    public ScheduleDecisionResponse processFailure(String machineId) {
        log.info("Processing failure decision flow for machine {}", machineId);

        ScheduleWithMetrics timeOpt = cpSatSchedulingService.solveTimeOptimal(machineId);
        ScheduleWithMetrics costOpt = cpSatSchedulingService.solveCostOptimal(machineId);

        log.info("OR-Tools solutions: time-optimal makespan={}min, cost-optimal disrupted={}",
                timeOpt.getMetrics().getMakespan(), costOpt.getMetrics().getDisruptedCount());

        String claudeAnalysis = claudeAgentService.analyze(machineId, timeOpt, costOpt);
        String[] parts = splitOptions(claudeAnalysis);

        Schedule original = store.getCurrentSchedule();
        String sessionId = UUID.randomUUID().toString();
        SchedulePair pair = new SchedulePair(machineId, original,
                timeOpt.getSchedule(), costOpt.getSchedule());
        DecisionSession session = new DecisionSession(sessionId, pair, claudeAnalysis,
                parts[0], parts[1], timeOpt.getMetrics(), costOpt.getMetrics());
        sessionCache.put(sessionId, session);
        store.setPendingSession(machineId, sessionId);

        log.info("Decision session {} created for machine {}", sessionId, machineId);
        return toResponse(session);
    }

    public Optional<ScheduleDecisionResponse> getSessionResponse(String sessionId) {
        DecisionSession session = sessionCache.get(sessionId);
        if (session == null || isExpired(session)) return Optional.empty();
        return Optional.of(toResponse(session));
    }

    public ChoiceResponse processChoice(String sessionId, String userMessage) {
        DecisionSession session = sessionCache.get(sessionId);

        if (session == null) {
            log.warn("Session {} not found", sessionId);
            return ChoiceResponse.needsClarification("Session not found. Please request a new failure analysis.");
        }

        if (isExpired(session)) {
            sessionCache.remove(sessionId);
            store.clearPendingSession(session.getSchedulePair().getFailedMachineId());
            log.warn("Session {} expired", sessionId);
            return ChoiceResponse.needsClarification("Session expired. Please request a new failure analysis.");
        }

        log.info("Interpreting user choice for session {}: \"{}\"", sessionId, userMessage);
        ChoiceResult result = choiceInterpreterService.interpretChoice(
                userMessage, session.getOptionAText(), session.getOptionBText());

        if (result.isNeedsClarification()) {
            return ChoiceResponse.needsClarification(result.getClarificationMessage());
        }

        Schedule chosen = "time".equals(result.getChoice())
                ? session.getSchedulePair().getTimeOptimal()
                : session.getSchedulePair().getCostOptimal();

        store.setCurrentSchedule(chosen);
        store.clearPendingSession(session.getSchedulePair().getFailedMachineId());
        sessionCache.remove(sessionId);

        log.info("Applied {} schedule for session {}.", result.getChoice(), sessionId);
        return ChoiceResponse.applied(chosen);
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredSessions() {
        long before = sessionCache.size();
        sessionCache.entrySet().removeIf(e -> {
            if (isExpired(e.getValue())) {
                store.clearPendingSession(e.getValue().getSchedulePair().getFailedMachineId());
                return true;
            }
            return false;
        });
        long removed = before - sessionCache.size();
        if (removed > 0) log.info("Cleaned up {} expired decision session(s)", removed);
    }

    private ScheduleDecisionResponse toResponse(DecisionSession session) {
        return new ScheduleDecisionResponse(
                session.getSessionId(),
                session.getClaudeAnalysis(),
                session.getOptionAText(),
                session.getOptionBText(),
                session.getSchedulePair().getTimeOptimal(),
                session.getSchedulePair().getCostOptimal(),
                session.getOptionAMetrics(),
                session.getOptionBMetrics(),
                session.getCreatedAt() + SESSION_TTL_MS);
    }

    private boolean isExpired(DecisionSession session) {
        return System.currentTimeMillis() - session.getCreatedAt() > SESSION_TTL_MS;
    }

    private String[] splitOptions(String analysis) {
        String optionA = "";
        String optionB = "";
        for (String line : analysis.split("\n")) {
            if (line.startsWith("OPTION A")) {
                optionA = line.replaceFirst("OPTION A \\(Fast\\):\\s*", "").trim();
            } else if (line.startsWith("OPTION B")) {
                optionB = line.replaceFirst("OPTION B \\(Cheap\\):\\s*", "").trim();
            }
        }
        if (optionA.isEmpty()) optionA = analysis;
        if (optionB.isEmpty()) optionB = analysis;
        return new String[]{optionA, optionB};
    }
}
