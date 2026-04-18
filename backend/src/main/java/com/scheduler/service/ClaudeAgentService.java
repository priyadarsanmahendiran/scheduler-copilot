package com.scheduler.service;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.client.AnthropicClient;
import com.scheduler.model.ChatMessage;
import com.scheduler.model.DecisionSession;
import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.ScheduleMetrics;
import com.scheduler.model.ScheduleWithMetrics;
import com.scheduler.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClaudeAgentService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeAgentService.class);
    private static final String MODEL = "claude-opus-4-7";
    private static final long MAX_TOKENS = 2048L;
    private static final int MAX_ITERATIONS = 10;

    private final AnthropicClient anthropicClient;
    private final InMemoryStore store;
    private final ObjectMapper objectMapper;

    public ClaudeAgentService(AnthropicClient anthropicClient,
                               InMemoryStore store,
                               ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public String analyze(String failedMachineId,
                          ScheduleWithMetrics timeOpt,
                          ScheduleWithMetrics costOpt) {
        AnalysisContext ctx = new AnalysisContext(failedMachineId, timeOpt, costOpt);
        List<Tool> tools = buildTools();

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(buildUserPrompt(failedMachineId))
                .build());

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(MAX_TOKENS)
                    .system(SYSTEM_PROMPT)
                    .messages(messages);
            for (Tool tool : tools) {
                paramsBuilder.addTool(tool);
            }
            MessageCreateParams params = paramsBuilder.build();

            Message response = anthropicClient.getSdkClient().messages().create(params);
            log.debug("Claude iteration {}: stopReason={}", iteration, response.stopReason());

            if (response.stopReason().map(StopReason.END_TURN::equals).orElse(false)) {
                return extractText(response);
            }

            if (!response.stopReason().map(StopReason.TOOL_USE::equals).orElse(false)) {
                return extractText(response);
            }

            // Add assistant message to history
            List<ContentBlockParam> assistantBlocks = toContentBlockParams(response.content());
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks)
                    .build());

            // Execute tools and collect results
            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                if (block.isToolUse()) {
                    ToolUseBlock toolUse = block.asToolUse();
                    String result = executeTool(toolUse, ctx);
                    toolResults.add(ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                    .toolUseId(toolUse.id())
                                    .content(result)
                                    .build()));
                }
            }

            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        log.warn("Claude agent hit max iterations for machine {}", failedMachineId);
        return "Analysis unavailable — agent exceeded iteration limit.";
    }

    public String chat(DecisionSession session, String userMessage) {
        List<MessageParam> messages = new ArrayList<>();

        // Seed the conversation with the original analysis as context
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("Here is the analysis I provided for the machine failure:\n\n"
                        + session.getClaudeAnalysis()
                        + "\n\nThe human operator will now ask follow-up questions. "
                        + "Answer concisely and stay grounded in the OR-Tools data.")
                .build());
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content("Understood. I'm ready to discuss the analysis and help the operator make an informed decision.")
                .build());

        // Replay prior turns in this session
        for (ChatMessage msg : session.getChatHistory()) {
            MessageParam.Role role = "user".equals(msg.getRole())
                    ? MessageParam.Role.USER
                    : MessageParam.Role.ASSISTANT;
            messages.add(MessageParam.builder()
                    .role(role)
                    .content(msg.getContent())
                    .build());
        }

        // Append the new user message
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userMessage)
                .build());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(512L)
                .system(SYSTEM_PROMPT)
                .messages(messages)
                .build();

        Message response = anthropicClient.getSdkClient().messages().create(params);
        return extractText(response);
    }

    private String executeTool(ToolUseBlock toolUse, AnalysisContext ctx) {
        try {
            JsonNode input = objectMapper.valueToTree(toolUse._input());
            return switch (toolUse.name()) {
                case "get_machine_status" -> getMachineStatus(input.path("machine_id").asText(""), ctx);
                case "get_time_optimal_schedule" -> getTimeOptimalSchedule(ctx);
                case "get_cost_optimal_schedule" -> getCostOptimalSchedule(ctx);
                case "check_cascade_risk" -> checkCascadeRisk(input.path("machine_id").asText(""), ctx);
                default -> "Unknown tool: " + toolUse.name();
            };
        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", toolUse.name(), e.getMessage());
            return "Error executing tool: " + e.getMessage();
        }
    }

    private String getMachineStatus(String machineId, AnalysisContext ctx) {
        if (machineId == null || machineId.isBlank()) {
            return store.getMachines().values().stream()
                    .map(m -> String.format("Machine %s: status=%s, existingLoad=%d min",
                            m.getId(), m.getStatus(),
                            currentLoad(m.getId())))
                    .collect(Collectors.joining("\n"));
        }
        Machine machine = store.getMachines().get(machineId);
        if (machine == null) return "Machine " + machineId + " not found";
        return String.format("Machine %s: status=%s, existingLoad=%d min",
                machine.getId(), machine.getStatus(), currentLoad(machineId));
    }

    private String getTimeOptimalSchedule(AnalysisContext ctx) {
        ScheduleMetrics m = ctx.timeOpt.getMetrics();
        return formatScheduleSummary("TIME-OPTIMAL (minimize makespan)", ctx.timeOpt, m);
    }

    private String getCostOptimalSchedule(AnalysisContext ctx) {
        ScheduleMetrics m = ctx.costOpt.getMetrics();
        return formatScheduleSummary("COST-OPTIMAL (minimize disruption)", ctx.costOpt, m);
    }

    private String checkCascadeRisk(String machineId, AnalysisContext ctx) {
        if (machineId == null || machineId.isBlank()) {
            return "Please specify a machine_id to check cascade risk.";
        }
        int currentLoadVal = currentLoad(machineId);
        int timeOptLoad = ctx.timeOpt.getMetrics().getMachineLoads().getOrDefault(machineId, 0);
        int costOptLoad = ctx.costOpt.getMetrics().getMachineLoads().getOrDefault(machineId, 0);

        int maxSafeLoad = estimateMaxSafeLoad();
        String risk;
        if (timeOptLoad > maxSafeLoad * 0.9) {
            risk = "HIGH — machine would be overloaded with time-optimal schedule";
        } else if (timeOptLoad > maxSafeLoad * 0.7) {
            risk = "MEDIUM — significant additional load";
        } else {
            risk = "LOW — load within safe operating range";
        }

        return String.format(
                "Machine %s cascade risk: %s\n" +
                "Current load: %d min | Time-optimal load: %d min | Cost-optimal load: %d min",
                machineId, risk, currentLoadVal, timeOptLoad, costOptLoad);
    }

    private String formatScheduleSummary(String label, ScheduleWithMetrics swm, ScheduleMetrics metrics) {
        StringBuilder sb = new StringBuilder(label).append("\n");
        sb.append(String.format("Makespan: %d min | Machines disrupted: %d\n",
                metrics.getMakespan(), metrics.getDisruptedCount()));
        sb.append("Assignments:\n");
        List<String> breachedJobs = new ArrayList<>();
        swm.getSchedule().getAssignments().forEach((machineId, jobs) -> {
            String jobSummary = jobs.stream()
                    .map(j -> j.getId() + "(" + j.getDuration() + "min)")
                    .collect(Collectors.joining(", "));
            sb.append(String.format("  %s: [%s] total=%d min\n",
                    machineId, jobSummary,
                    jobs.stream().mapToInt(Job::getDuration).sum()));
            int elapsed = 0;
            for (Job j : jobs) {
                elapsed += j.getDuration();
                if (j.getDeadline() > 0 && elapsed > j.getDeadline()) {
                    breachedJobs.add(j.getId() + "(deadline=" + j.getDeadline() + "min,finish=" + elapsed + "min)");
                }
            }
        });
        if (!breachedJobs.isEmpty()) {
            sb.append(String.format("SLA BREACHES: %d job(s) will miss deadline — %s\n",
                    breachedJobs.size(), String.join(", ", breachedJobs)));
        } else {
            sb.append("SLA: All jobs meet their deadlines\n");
        }
        return sb.toString();
    }

    private int currentLoad(String machineId) {
        if (store.getCurrentSchedule() == null) return 0;
        return store.getCurrentSchedule().getAssignments()
                .getOrDefault(machineId, List.of()).stream()
                .mapToInt(Job::getDuration).sum();
    }

    private int estimateMaxSafeLoad() {
        // Heuristic: safe load is 150% of average current load across running machines
        List<Machine> running = store.getMachines().values().stream()
                .filter(m -> "RUNNING".equals(m.getStatus()))
                .collect(Collectors.toList());
        if (running.isEmpty()) return Integer.MAX_VALUE;
        int avgLoad = running.stream()
                .mapToInt(m -> currentLoad(m.getId()))
                .sum() / running.size();
        return (int) (avgLoad * 1.5) + 60;
    }

    private List<ContentBlockParam> toContentBlockParams(List<ContentBlock> blocks) {
        List<ContentBlockParam> result = new ArrayList<>();
        for (ContentBlock block : blocks) {
            if (block.isText()) {
                result.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(block.asText().text()).build()));
            } else if (block.isToolUse()) {
                result.add(ContentBlockParam.ofToolUse(block.asToolUse().toParam()));
            }
        }
        return result;
    }

    private String extractText(Message response) {
        return response.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .collect(Collectors.joining("\n"));
    }

    private String buildUserPrompt(String failedMachineId) {
        return String.format(
                "Machine %s has failed. Use the available tools to:\n" +
                "1. Check all machine statuses and loads\n" +
                "2. Retrieve both rescheduling options from OR-Tools\n" +
                "3. Check cascade risk for heavily loaded machines\n" +
                "4. Provide a structured analysis in this EXACT format:\n\n" +
                "SITUATION: [1-2 sentences on what failed and immediate impact]\n\n" +
                "OPTION A (Fast): [time-optimal schedule summary — makespan, which machines, key tradeoffs]\n\n" +
                "OPTION B (Cheap): [cost-optimal schedule summary — disruption count, which machines, key tradeoffs]\n\n" +
                "CASCADE RISK: [specific risk assessment for each option]\n\n" +
                "MY RECOMMENDATION: [which option you recommend and why, in 1-2 sentences]",
                failedMachineId);
    }

    private List<Tool> buildTools() {
        return List.of(
                Tool.builder()
                        .name("get_machine_status")
                        .description("Get current status and load for a specific machine or all machines. "
                                + "Pass an empty machine_id to get all machines.")
                        .inputSchema(Tool.InputSchema.builder()
                                .properties(Tool.InputSchema.Properties.builder()
                                        .putAdditionalProperty("machine_id",
                                                JsonValue.from(Map.of(
                                                        "type", "string",
                                                        "description", "Machine ID, or empty string for all machines")))
                                        .build())
                                .build())
                        .build(),
                Tool.builder()
                        .name("get_time_optimal_schedule")
                        .description("Get the OR-Tools CP-SAT time-optimal (minimum makespan) schedule "
                                + "with metrics for the failed machine's jobs.")
                        .inputSchema(Tool.InputSchema.builder()
                                .properties(Tool.InputSchema.Properties.builder().build())
                                .build())
                        .build(),
                Tool.builder()
                        .name("get_cost_optimal_schedule")
                        .description("Get the OR-Tools CP-SAT cost-optimal (minimum disruption) schedule "
                                + "with metrics for the failed machine's jobs.")
                        .inputSchema(Tool.InputSchema.builder()
                                .properties(Tool.InputSchema.Properties.builder().build())
                                .build())
                        .build(),
                Tool.builder()
                        .name("check_cascade_risk")
                        .description("Check the cascade failure risk for a specific machine — "
                                + "how much additional load each rescheduling option would add.")
                        .inputSchema(Tool.InputSchema.builder()
                                .properties(Tool.InputSchema.Properties.builder()
                                        .putAdditionalProperty("machine_id",
                                                JsonValue.from(Map.of(
                                                        "type", "string",
                                                        "description", "The machine ID to check")))
                                        .build())
                                .required(List.of("machine_id"))
                                .build())
                        .build()
        );
    }

    private static final String SYSTEM_PROMPT =
            "You are an expert manufacturing scheduling analyst. Your role is to analyze machine failure "
            + "scenarios and present clear, actionable rescheduling options to human operators. "
            + "You have access to OR-Tools CP-SAT optimized schedules — trust these as mathematically "
            + "optimal. Focus your analysis on cascade risks, operational tradeoffs, and a clear "
            + "recommendation. Be concise and precise.";

    private static class AnalysisContext {
        final String failedMachineId;
        final ScheduleWithMetrics timeOpt;
        final ScheduleWithMetrics costOpt;

        AnalysisContext(String failedMachineId, ScheduleWithMetrics timeOpt, ScheduleWithMetrics costOpt) {
            this.failedMachineId = failedMachineId;
            this.timeOpt = timeOpt;
            this.costOpt = costOpt;
        }
    }
}
