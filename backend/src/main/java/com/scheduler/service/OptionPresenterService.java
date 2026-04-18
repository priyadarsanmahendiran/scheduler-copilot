package com.scheduler.service;

import com.scheduler.client.AnthropicClient;
import com.scheduler.model.Job;
import com.scheduler.model.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OptionPresenterService {
    private static final Logger log = LoggerFactory.getLogger(OptionPresenterService.class);

    private final AnthropicClient anthropicClient;

    public OptionPresenterService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    public String presentOptions(String failedMachineId,
                                  Schedule original,
                                  Schedule timeOptimal,
                                  Schedule costOptimal) {
        String prompt = buildPrompt(failedMachineId, original, timeOptimal, costOptimal);
        log.info("Requesting LLM explanation for failure of machine {}", failedMachineId);
        String response = anthropicClient.sendMessage(prompt);
        log.info("LLM explanation received for machine {}", failedMachineId);
        return response;
    }

    private String buildPrompt(String failedMachineId,
                                Schedule original,
                                Schedule timeOptimal,
                                Schedule costOptimal) {
        List<Job> affectedJobs = original.getAssignments().getOrDefault(failedMachineId, List.of());
        String affectedSummary = affectedJobs.isEmpty()
                ? "none (already redistributed)"
                : affectedJobs.stream()
                        .map(j -> j.getId() + " (" + j.getDuration() + "min)")
                        .collect(Collectors.joining(", "));

        return String.format("""
                Machine %s has failed.
                Jobs originally on %s: %s

                TIME-OPTIMAL Schedule (round-robin across remaining machines):
                %s

                COST-OPTIMAL Schedule (all jobs consolidated to least-loaded machine):
                %s

                Explain both options in 2-3 sentences each. Use operator-friendly language \
                a factory floor manager would understand. Focus on which jobs move where and \
                the operational impact.

                Format your response exactly as:
                OPTION A (Fast): <explanation>
                OPTION B (Cheap): <explanation>
                """,
                failedMachineId, failedMachineId, affectedSummary,
                formatSchedule(timeOptimal),
                formatSchedule(costOptimal));
    }

    private String formatSchedule(Schedule schedule) {
        return schedule.getAssignments().entrySet().stream()
                .map(e -> {
                    int total = e.getValue().stream().mapToInt(Job::getDuration).sum();
                    String jobs = e.getValue().stream()
                            .map(j -> j.getId() + "(" + j.getDuration() + "min)")
                            .collect(Collectors.joining(", "));
                    return "  " + e.getKey() + ": [" + jobs + "] total=" + total + "min";
                })
                .collect(Collectors.joining("\n"));
    }
}
