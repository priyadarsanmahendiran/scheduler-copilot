package com.scheduler.service;

import com.scheduler.client.AnthropicClient;
import com.scheduler.model.Machine;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PredictiveMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(PredictiveMaintenanceService.class);

    // Minimum samples for the CV (jitter) check
    private static final int MIN_SAMPLES = 12;
    // Trend check needs a proper baseline: at least MIN_SAMPLES entries before the 3-sample recent window
    private static final int TREND_MIN_SAMPLES = MIN_SAMPLES + 3;
    // Coefficient of variation threshold — high jitter
    private static final double CV_THRESHOLD = 20.0;
    // Ratio of recent-3 average to overall mean — upward trend
    private static final double TREND_THRESHOLD = 1.35;
    // How long to wait before re-assessing the same machine after a Claude call
    private static final long COOLDOWN_MS = 300_000;

    private final Map<String, Long> lastAssessmentTime = new ConcurrentHashMap<>();
    private final AnthropicClient anthropicClient;
    private final InMemoryStore store;
    private final SseBroadcaster broadcaster;

    public PredictiveMaintenanceService(AnthropicClient anthropicClient, InMemoryStore store,
                                        SseBroadcaster broadcaster) {
        this.anthropicClient = anthropicClient;
        this.store = store;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedRate = 5000, initialDelay = 30_000)
    public void scan() {
        store.getMachines().values().forEach(this::evaluate);
    }

    private void evaluate(Machine machine) {
        if (!"RUNNING".equals(machine.getStatus())) {
            machine.setRiskLevel(null);
            machine.setRiskReason(null);
            return;
        }

        List<Long> intervals = store.getHeartbeatIntervals(machine.getId());
        if (intervals.size() < MIN_SAMPLES) return;

        double mean   = mean(intervals);
        double stdDev = stdDev(intervals, mean);
        double cv     = (stdDev / mean) * 100;

        // Trend: compare the last 3 samples against a stable baseline.
        // Only computed once we have enough history so the baseline isn't contaminated by
        // the recent window itself (need at least TREND_MIN_SAMPLES = MIN_SAMPLES + 3).
        double trendRatio = 1.0;
        if (intervals.size() >= TREND_MIN_SAMPLES) {
            List<Long> baseline = intervals.subList(0, intervals.size() - 3);
            List<Long> recent   = intervals.subList(intervals.size() - 3, intervals.size());
            trendRatio = mean(recent) / mean(baseline);
        }

        boolean anomalous = cv > CV_THRESHOLD || trendRatio > TREND_THRESHOLD;
        if (!anomalous) return;

        Long last = lastAssessmentTime.get(machine.getId());
        if (last != null && System.currentTimeMillis() - last < COOLDOWN_MS) return;

        log.info("Anomaly on {}: CV={}%  trend={}x baseline — escalating to Claude Haiku",
                machine.getId(),
                String.format("%.1f", cv),
                String.format("%.2f", trendRatio));

        try {
            callHaiku(machine, intervals, mean, stdDev, cv, trendRatio);
        } catch (Exception e) {
            log.warn("Predictive assessment failed for {}: {}", machine.getId(), e.getMessage());
        }
    }

    private void callHaiku(Machine machine, List<Long> intervals,
                            double mean, double stdDev, double cv, double trendRatio) {
        lastAssessmentTime.put(machine.getId(), System.currentTimeMillis());

        String intervalStr = intervals.stream()
                .map(i -> String.format("%.2f", i / 1000.0))
                .collect(Collectors.joining(", "));

        String prompt = String.format(
                "Machine %s heartbeat intervals (seconds): [%s]\n"
                + "Stats: mean=%.2fs  stddev=%.2fs  CV=%.1f%%  recent trend=+%.0f%% vs baseline\n"
                + "Normal heartbeat interval is 2.0s.\n\n"
                + "Reply in EXACTLY this format, no other text:\n"
                + "RISK: HIGH\n"
                + "REASON: one concise sentence explaining the pattern",
                machine.getId(), intervalStr,
                mean / 1000, stdDev / 1000, cv, (trendRatio - 1) * 100);

        String response = anthropicClient.sendMessage(prompt);

        String riskLevel = parseField(response, "RISK:");
        String reason    = parseField(response, "REASON:");

        if ("HIGH".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
            machine.setRiskLevel(riskLevel);
            machine.setRiskReason(reason != null ? reason : "Anomalous heartbeat pattern detected.");
            log.info("Predictive alert set: {} → {} — {}", machine.getId(), riskLevel, reason);
        } else {
            // Claude says LOW or unrecognised — clear any previous warning
            machine.setRiskLevel(null);
            machine.setRiskReason(null);
        }
        broadcaster.broadcast("machines", store.getMachines().values());
    }

    private String parseField(String text, String prefix) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith(prefix)) return t.substring(prefix.length()).trim();
        }
        return null;
    }

    private double mean(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private double stdDev(List<Long> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }
}
