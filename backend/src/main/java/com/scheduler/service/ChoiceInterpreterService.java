package com.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.client.AnthropicClient;
import com.scheduler.model.ChoiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChoiceInterpreterService {
    private static final Logger log = LoggerFactory.getLogger(ChoiceInterpreterService.class);
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public ChoiceInterpreterService(AnthropicClient anthropicClient, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    public ChoiceResult interpretChoice(String userMessage, String optionAText, String optionBText) {
        String prompt = buildPrompt(userMessage, optionAText, optionBText);
        log.info("Interpreting user choice: \"{}\"", userMessage);
        String response = anthropicClient.sendMessage(prompt);
        log.info("LLM raw choice response: {}", response);
        return parseChoiceResult(response);
    }

    private String buildPrompt(String userMessage, String optionAText, String optionBText) {
        return String.format("""
                An operator was presented with two schedule recovery options after a machine failure.

                OPTION A (Fast / Time-Optimal): %s
                OPTION B (Cheap / Cost-Optimal): %s

                The operator responded: "%s"

                Which option did the operator choose? Return ONLY a JSON object in this exact format:
                {"choice": "time", "confidence": 0.95}
                where "choice" is either "time" (Option A) or "cost" (Option B), \
                and "confidence" is between 0 and 1.
                If the intent is completely unclear, return: {"choice": "unclear", "confidence": 0.0}
                """,
                optionAText, optionBText, userMessage);
    }

    private ChoiceResult parseChoiceResult(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            String choice = node.get("choice").asText();
            double confidence = node.get("confidence").asDouble();

            log.info("Parsed choice: {} (confidence={})", choice, confidence);

            if ("unclear".equals(choice) || confidence < CONFIDENCE_THRESHOLD) {
                return ChoiceResult.clarification(
                        "Did you mean time-optimal (Option A — Fast) or cost-optimal (Option B — Cheap)?");
            }
            return ChoiceResult.of(choice, confidence);
        } catch (Exception e) {
            log.warn("Failed to parse LLM choice response: '{}'. Requesting clarification.", response, e);
            return ChoiceResult.clarification(
                    "Did you mean time-optimal (Option A — Fast) or cost-optimal (Option B — Cheap)?");
        }
    }

    private String extractJson(String text) {
        if (text.contains("```json")) {
            return text.replaceAll("(?s).*```json\\s*", "").replaceAll("(?s)```.*", "").trim();
        }
        if (text.contains("```")) {
            return text.replaceAll("(?s).*```\\s*", "").replaceAll("(?s)```.*", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}
