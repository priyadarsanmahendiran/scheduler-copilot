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
import java.util.Map;
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

    // Sentinel values written to pendingSessionByMachine while analysis is running.
    // Different values let the frontend distinguish failure vs recovery mode without
    // relying on machine status, which may not have propagated via SSE yet.
    public static final String ANALYZING_SENTINEL = "ANALYZING";
    public static final String ANALYZING_RECOVERY_SENTINEL = "ANALYZING_RECOVERY";

    public ScheduleDecisionResponse processFailure(String machineId) {
        log.info("Processing failure decision flow for machine {}", machineId);

        store.setPendingSession(machineId, ANALYZING_SENTINEL);

        // Invalidate every other machine's pending state — both completed sessions AND
        // in-flight ANALYZING sentinels. Any concurrent processFailure thread for another
        // machine will see its sentinel was cleared and will discard its result at write time.
        new java.util.ArrayList<>(store.getPendingSessionByMachine().keySet()).stream()
                .filter(m -> !machineId.equals(m))
                .forEach(m -> {
                    store.clearPendingSession(m);
                    log.info("Cleared pending state for {} (superseded by {} analysis)", m, machineId);
                });
        sessionCache.entrySet().removeIf(entry ->
                !machineId.equals(entry.getValue().getSchedulePair().getFailedMachineId()));

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
                claudeUnavailable, slpA, slpB, "failure");

        // Guard: a newer failure was triggered while we were computing. Our schedule was
        // solved without all currently-down machines — persisting it would show invalid
        // redistribution to machines that are also now offline. Discard silently.
        if (!ANALYZING_SENTINEL.equals(store.getPendingSession(machineId))) {
            log.info("Analysis for {} superseded by newer failure, discarding stale session", machineId);
            return toResponse(session);
        }

        sessionCache.put(sessionId, session);
        store.setPendingSession(machineId, sessionId);

        log.info("Decision session {} created for machine {}", sessionId, machineId);
        return toResponse(session);
    }

    public ScheduleDecisionResponse processRecovery(String machineId) {
        log.info("Processing recovery decision flow for machine {}", machineId);

        // Clear stale recovery sessions for ALL other machines. Those analyses were
        // computed before this machine came back online, so they don't reflect the full
        // set of available machines. Any operator action on them would reschedule jobs
        // onto an incomplete machine set — producing conflicts.
        new java.util.ArrayList<>(store.getPendingSessionByMachine().keySet()).stream()
                .filter(m -> !machineId.equals(m))
                .forEach(m -> {
                    String pending = store.getPendingSession(m);
                    if (pending == null) return;
                    if (ANALYZING_RECOVERY_SENTINEL.equals(pending)) {
                        store.clearPendingSession(m);
                        log.info("Cleared in-flight recovery for {} (superseded by {} recovery)", m, machineId);
                    } else {
                        DecisionSession s = sessionCache.get(pending);
                        if (s != null && "recovery".equals(s.getSessionType())) {
                            sessionCache.remove(pending);
                            store.clearPendingSession(m);
                            log.info("Cleared stale recovery session {} for {} (superseded by {} recovery)",
                                    pending, m, machineId);
                        }
                    }
                });

        // Clear any existing session for this machine (stale failure or prior recovery)
        sessionCache.entrySet().removeIf(entry -> {
            if (machineId.equals(entry.getValue().getSchedulePair().getFailedMachineId())) {
                log.info("Cleared stale session {} for {} (superseded by recovery)", entry.getKey(), machineId);
                return true;
            }
            return false;
        });

        store.setPendingSession(machineId, ANALYZING_RECOVERY_SENTINEL);

        ScheduleWithMetrics rebalance = cpSatSchedulingService.solveGlobalRebalance();
        ScheduleWithMetrics restore   = buildRestoreSchedule(machineId);

        log.info("Recovery options: rebalance makespan={}min, restore makespan={}min",
                rebalance.getMetrics().getMakespan(), restore.getMetrics().getMakespan());

        boolean claudeUnavailable = false;
        String claudeAnalysis;
        try {
            claudeAnalysis = claudeAgentService.analyzeRecovery(machineId, rebalance, restore);
        } catch (Exception e) {
            log.warn("Claude API unavailable for recovery analysis: {}", e.getMessage());
            claudeAnalysis = buildFallbackRecoveryAnalysis(machineId, rebalance, restore);
            claudeUnavailable = true;
        }

        String[] parts = splitOptions(claudeAnalysis);

        SlaBreachResult slpA = checkSlaBreaches(rebalance.getSchedule());
        SlaBreachResult slpB = checkSlaBreaches(restore.getSchedule());

        Schedule original = store.getCurrentSchedule();
        String sessionId = UUID.randomUUID().toString();
        SchedulePair pair = new SchedulePair(machineId, original,
                rebalance.getSchedule(), restore.getSchedule());
        DecisionSession session = new DecisionSession(sessionId, pair, claudeAnalysis,
                parts[0], parts[1], rebalance.getMetrics(), restore.getMetrics(),
                claudeUnavailable, slpA, slpB, "recovery");

        // Write-time guard: a later recovery cleared our sentinel while we were computing.
        // Our schedule doesn't account for all recovered machines — discard it.
        if (!ANALYZING_RECOVERY_SENTINEL.equals(store.getPendingSession(machineId))) {
            log.info("Recovery analysis for {} superseded, discarding stale session", machineId);
            return toResponse(session);
        }

        sessionCache.put(sessionId, session);
        store.setPendingSession(machineId, sessionId);

        log.info("Recovery session {} created for machine {}", sessionId, machineId);
        return toResponse(session);
    }

    private ScheduleWithMetrics buildRestoreSchedule(String recoveredMachineId) {
        Schedule current = store.getCurrentSchedule();
        Map<String, List<Job>> currentAssignments = current != null ? current.getAssignments() : Map.of();

        Map<String, List<Job>> newAssignments = new java.util.LinkedHashMap<>();
        List<Job> jobsToRestore = new ArrayList<>();

        for (Map.Entry<String, List<Job>> entry : currentAssignments.entrySet()) {
            String machineId = entry.getKey();
            if (machineId.equals(recoveredMachineId)) {
                newAssignments.put(machineId, new ArrayList<>(entry.getValue()));
            } else {
                List<Job> stay = new ArrayList<>();
                for (Job job : entry.getValue()) {
                    if (job.getMachineId().equals(recoveredMachineId)) {
                        jobsToRestore.add(job);
                    } else {
                        stay.add(job);
                    }
                }
                if (!stay.isEmpty()) newAssignments.put(machineId, stay);
            }
        }
        newAssignments.put(recoveredMachineId, jobsToRestore);

        Map<String, Integer> loads = new java.util.HashMap<>();
        for (Map.Entry<String, List<Job>> e : newAssignments.entrySet()) {
            loads.put(e.getKey(), e.getValue().stream().mapToInt(Job::getDuration).sum());
        }
        int makespan = loads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        // Count machines whose job list actually changed
        int disrupted = (int) newAssignments.entrySet().stream()
                .filter(e -> {
                    List<Job> before = currentAssignments.getOrDefault(e.getKey(), List.of());
                    return !e.getValue().equals(before);
                })
                .count();

        return new ScheduleWithMetrics(
                new Schedule(newAssignments),
                new ScheduleMetrics(makespan, disrupted, loads));
    }

    private String buildFallbackRecoveryAnalysis(String machineId,
                                                  ScheduleWithMetrics rebalance,
                                                  ScheduleWithMetrics restore) {
        ScheduleMetrics a = rebalance.getMetrics();
        ScheduleMetrics b = restore.getMetrics();
        String available = rebalance.getSchedule().getAssignments().keySet()
                .stream().sorted().collect(java.util.stream.Collectors.joining(", "));
        return String.format(
                "SITUATION: Machine %s has come back online. OR-Tools CP-SAT has computed two "
                + "recovery options for reintegrating it into the production schedule (%s).\n\n"
                + "OPTION A (Rebalance): Full re-optimisation across all machines. "
                + "Makespan: %d minutes, affecting %d machine(s). "
                + "Redistributes all jobs optimally to minimise total completion time.\n\n"
                + "OPTION B (Restore): Original jobs returned to %s. "
                + "Makespan: %d minutes, affecting %d machine(s). "
                + "Minimal disruption — other machines keep their current assignments.\n\n"
                + "CASCADE RISK: Verify %s is fully stable before applying Option A. "
                + "Option B is safer if the machine's recovery is uncertain.\n\n"
                + "MY RECOMMENDATION: Choose Option B (Restore) for a conservative recovery. "
                + "Choose Option A (Rebalance) if throughput is the priority and %s is confirmed stable.",
                machineId, available,
                a.getMakespan(), a.getDisruptedCount(),
                machineId, b.getMakespan(), b.getDisruptedCount(),
                machineId, machineId);
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
                session.getOptionBSla(),
                session.getSessionType());
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
        String text = analysis.replaceAll("\\*+", "");
        String upper = text.toUpperCase();

        int aIdx = upper.indexOf("OPTION A");
        int bIdx = upper.indexOf("OPTION B");
        int cIdx = upper.indexOf("CASCADE");
        int rIdx = upper.indexOf("MY RECOMMENDATION");
        if (rIdx < 0) rIdx = upper.indexOf("MY RECOMMEND");

        String optionA = extractSection(text, aIdx, bIdx > aIdx ? bIdx : -1);
        String optionB = extractSection(text, bIdx,
                cIdx > bIdx ? cIdx : (rIdx > bIdx ? rIdx : -1));

        return new String[]{
            optionA != null ? optionA : analysis,
            optionB != null ? optionB : analysis
        };
    }

    private String extractSection(String text, int sectionStart, int sectionEnd) {
        if (sectionStart < 0) return null;
        // Find end of header line
        int lineEnd = text.indexOf('\n', sectionStart);
        if (lineEnd < 0) lineEnd = text.length();
        // Only treat a colon as a header delimiter if it appears on the header line itself.
        // A colon in the body text (e.g. "Makespan: 120min") must not be used as the split point.
        int colonIdx = text.indexOf(':', sectionStart);
        int contentStart = (colonIdx >= 0 && colonIdx < lineEnd) ? colonIdx + 1 : lineEnd + 1;
        // Skip blank lines between header and content
        while (contentStart < text.length()
                && (text.charAt(contentStart) == '\n' || text.charAt(contentStart) == '\r')) {
            contentStart++;
        }
        if (contentStart >= text.length()) return null;
        if (sectionEnd > contentStart) {
            return text.substring(contentStart, sectionEnd).trim();
        }
        return text.substring(contentStart).trim();
    }
}
