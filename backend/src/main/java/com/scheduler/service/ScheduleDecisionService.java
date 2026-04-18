package com.scheduler.service;

import com.scheduler.model.ChoiceResponse;
import com.scheduler.model.ChoiceResult;
import com.scheduler.model.DecisionSession;
import com.scheduler.model.Job;
import com.scheduler.model.Schedule;
import com.scheduler.model.ScheduleDecisionResponse;
import com.scheduler.model.ScheduleMetrics;
import com.scheduler.model.SchedulePair;
import com.scheduler.model.ScheduleWithMetrics;
import com.scheduler.model.SlaBreachResult;
import com.scheduler.store.InMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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

    // Sentinel value written to pendingSessionByMachine while analysis is running.
    // The frontend polls this map and shows a spinner for any machine with this value.
    public static final String ANALYZING_SENTINEL = "ANALYZING";

    public ScheduleDecisionResponse processFailure(String machineId) {
        log.info("Processing failure decision flow for machine {}", machineId);

        // Write sentinel immediately so the UI can show "analyzing" state before Claude finishes.
        store.setPendingSession(machineId, ANALYZING_SENTINEL);

        // Clear stale sessions for other machines — this new analysis covers all currently-down
        // machines together, so any earlier single-machine session is now superseded.
        sessionCache.entrySet().removeIf(entry -> {
            String staleMachine = entry.getValue().getSchedulePair().getFailedMachineId();
            if (!machineId.equals(staleMachine)) {
                store.clearPendingSession(staleMachine);
                log.info("Cleared stale session {} for {} (superseded by {} analysis)",
                        entry.getKey(), staleMachine, machineId);
                return true;
            }
            return false;
        });

        ScheduleWithMetrics timeOpt = cpSatSchedulingService.solveTimeOptimal(machineId);
        ScheduleWithMetrics costOpt = cpSatSchedulingService.solveCostOptimal(machineId);

        log.info("OR-Tools solutions: time-optimal makespan={}min, cost-optimal disrupted={}",
                timeOpt.getMetrics().getMakespan(), costOpt.getMetrics().getDisruptedCount());

        boolean claudeUnavailable = false;
        String claudeAnalysis;
        try {
            claudeAnalysis = claudeAgentService.analyze(machineId, timeOpt, costOpt);
        } catch (Exception e) {
            log.warn("Claude API unavailable, using OR-Tools fallback analysis: {}", e.getMessage());
            claudeAnalysis = buildFallbackAnalysis(machineId, timeOpt, costOpt);
            claudeUnavailable = true;
        }

        String[] parts = splitOptions(claudeAnalysis);

        SlaBreachResult slpA = checkSlaBreaches(timeOpt.getSchedule());
        SlaBreachResult slpB = checkSlaBreaches(costOpt.getSchedule());

        Schedule original = store.getCurrentSchedule();
        String sessionId = UUID.randomUUID().toString();
        SchedulePair pair = new SchedulePair(machineId, original,
                timeOpt.getSchedule(), costOpt.getSchedule());
        DecisionSession session = new DecisionSession(sessionId, pair, claudeAnalysis,
                parts[0], parts[1], timeOpt.getMetrics(), costOpt.getMetrics(),
                claudeUnavailable, slpA, slpB);
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

    public Optional<String> chat(String sessionId, String userMessage) {
        DecisionSession session = sessionCache.get(sessionId);
        if (session == null || isExpired(session)) return Optional.empty();

        String reply;
        try {
            reply = claudeAgentService.chat(session, userMessage);
        } catch (Exception e) {
            log.warn("Claude API unavailable for chat: {}", e.getMessage());
            reply = "I'm unable to respond right now due to an API connectivity issue. "
                  + "Please review the OR-Tools metrics above to make your decision.";
        }
        session.addChatMessage("user", userMessage);
        session.addChatMessage("assistant", reply);
        return Optional.of(reply);
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
                session.getCreatedAt() + SESSION_TTL_MS,
                session.isClaudeUnavailable(),
                session.getOptionASla(),
                session.getOptionBSla());
    }

    private SlaBreachResult checkSlaBreaches(Schedule schedule) {
        List<String> breachedIds = new ArrayList<>();
        for (List<Job> jobs : schedule.getAssignments().values()) {
            int elapsed = 0;
            for (Job job : jobs) {
                elapsed += job.getDuration();
                if (job.getDeadline() > 0 && elapsed > job.getDeadline()) {
                    breachedIds.add(job.getId());
                }
            }
        }
        return new SlaBreachResult(breachedIds.size(), breachedIds);
    }

    private boolean isExpired(DecisionSession session) {
        return System.currentTimeMillis() - session.getCreatedAt() > SESSION_TTL_MS;
    }

    private String buildFallbackAnalysis(String machineId,
                                         ScheduleWithMetrics timeOpt,
                                         ScheduleWithMetrics costOpt) {
        ScheduleMetrics a = timeOpt.getMetrics();
        ScheduleMetrics b = costOpt.getMetrics();
        String available = timeOpt.getSchedule().getAssignments().keySet()
                .stream().sorted().collect(Collectors.joining(", "));
        return String.format(
                "SITUATION: Machine %s has gone offline. OR-Tools CP-SAT has computed two rescheduling "
                + "options redistributing its jobs across available machines (%s).\n\n"
                + "OPTION A (Fast): Time-optimal schedule minimising total completion time. "
                + "Makespan: %d minutes, affecting %d machine(s). "
                + "Spreads load evenly to finish all jobs as quickly as possible.\n\n"
                + "OPTION B (Cheap): Disruption-minimal schedule limiting machines affected. "
                + "Makespan: %d minutes, touching only %d machine(s). "
                + "Consolidates work to reduce downstream process disruption.\n\n"
                + "CASCADE RISK: Option A spreads load across more machines, keeping per-machine "
                + "workload lower. Option B concentrates work on fewer machines — verify those "
                + "machines have sufficient capacity headroom before applying.\n\n"
                + "MY RECOMMENDATION: Choose Option A to minimise total production time. "
                + "Choose Option B to limit disruption to other machines and processes.",
                machineId, available,
                a.getMakespan(), a.getDisruptedCount(),
                b.getMakespan(), b.getDisruptedCount());
    }

    private String[] splitOptions(String analysis) {
        java.util.regex.Matcher mA = java.util.regex.Pattern.compile(
                "OPTION A[^:]*:\\s*([\\s\\S]*?)(?=\\n\\nOPTION B|\\nOPTION B|\\n\\nCASCADE|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(analysis);
        java.util.regex.Matcher mB = java.util.regex.Pattern.compile(
                "OPTION B[^:]*:\\s*([\\s\\S]*?)(?=\\n\\nCASCADE|\\nCASCADE|\\n\\nMY RECOMMEND|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(analysis);
        String optionA = mA.find() ? mA.group(1).trim() : analysis;
        String optionB = mB.find() ? mB.group(1).trim() : analysis;
        return new String[]{optionA, optionB};
    }
}
