package com.scheduler.client;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class AnthropicClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String SYSTEM_PROMPT =
            "You are a manufacturing scheduling assistant. Explain tradeoffs clearly. Parse user intent accurately.";
    private static final String MODEL = "claude-haiku-4-5";
    private static final long MAX_TOKENS = 1000L;

    private com.anthropic.client.AnthropicClient sdkClient;

    @PostConstruct
    public void init() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY is not set — LLM features will be unavailable");
            this.sdkClient = null;
            return;
        }
        try {
            this.sdkClient = AnthropicOkHttpClient.fromEnv();
            log.info("Anthropic client initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize Anthropic client: {}", e.getMessage());
            this.sdkClient = null;
        }
    }

    public com.anthropic.client.AnthropicClient getSdkClient() {
        if (sdkClient == null) {
            throw new IllegalStateException(
                    "Anthropic client not initialized. Set the ANTHROPIC_API_KEY environment variable.");
        }
        return sdkClient;
    }

    public String sendMessage(String userPrompt) {
        if (sdkClient == null) {
            throw new IllegalStateException(
                    "Anthropic client not initialized. Set the ANTHROPIC_API_KEY environment variable.");
        }

        log.debug("Sending message to Claude: {}", userPrompt.substring(0, Math.min(100, userPrompt.length())));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userPrompt)
                .build();

        return sdkClient.messages().create(params)
                .content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
    }
}
