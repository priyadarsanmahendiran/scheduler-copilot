package com.scheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DecisionSession {
    private String sessionId;
    private SchedulePair schedulePair;
    private String claudeAnalysis;
    private String optionAText;
    private String optionBText;
    private ScheduleMetrics optionAMetrics;
    private ScheduleMetrics optionBMetrics;
    private long createdAt;
    private boolean claudeUnavailable;
    private SlaBreachResult optionASla;
    private SlaBreachResult optionBSla;
    private final List<ChatMessage> chatHistory = Collections.synchronizedList(new ArrayList<>());

    public DecisionSession() {}

    public DecisionSession(String sessionId, SchedulePair schedulePair,
                           String claudeAnalysis, String optionAText, String optionBText,
                           ScheduleMetrics optionAMetrics, ScheduleMetrics optionBMetrics,
                           boolean claudeUnavailable,
                           SlaBreachResult optionASla, SlaBreachResult optionBSla) {
        this.sessionId = sessionId;
        this.schedulePair = schedulePair;
        this.claudeAnalysis = claudeAnalysis;
        this.optionAText = optionAText;
        this.optionBText = optionBText;
        this.optionAMetrics = optionAMetrics;
        this.optionBMetrics = optionBMetrics;
        this.claudeUnavailable = claudeUnavailable;
        this.optionASla = optionASla;
        this.optionBSla = optionBSla;
        this.createdAt = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public SchedulePair getSchedulePair() { return schedulePair; }
    public void setSchedulePair(SchedulePair schedulePair) { this.schedulePair = schedulePair; }

    public String getClaudeAnalysis() { return claudeAnalysis; }
    public void setClaudeAnalysis(String claudeAnalysis) { this.claudeAnalysis = claudeAnalysis; }

    public String getOptionAText() { return optionAText; }
    public void setOptionAText(String optionAText) { this.optionAText = optionAText; }

    public String getOptionBText() { return optionBText; }
    public void setOptionBText(String optionBText) { this.optionBText = optionBText; }

    public ScheduleMetrics getOptionAMetrics() { return optionAMetrics; }
    public void setOptionAMetrics(ScheduleMetrics optionAMetrics) { this.optionAMetrics = optionAMetrics; }

    public ScheduleMetrics getOptionBMetrics() { return optionBMetrics; }
    public void setOptionBMetrics(ScheduleMetrics optionBMetrics) { this.optionBMetrics = optionBMetrics; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isClaudeUnavailable() { return claudeUnavailable; }
    public void setClaudeUnavailable(boolean claudeUnavailable) { this.claudeUnavailable = claudeUnavailable; }

    public SlaBreachResult getOptionASla() { return optionASla; }
    public void setOptionASla(SlaBreachResult optionASla) { this.optionASla = optionASla; }

    public SlaBreachResult getOptionBSla() { return optionBSla; }
    public void setOptionBSla(SlaBreachResult optionBSla) { this.optionBSla = optionBSla; }

    public List<ChatMessage> getChatHistory() { return chatHistory; }

    public void addChatMessage(String role, String content) {
        chatHistory.add(new ChatMessage(role, content));
    }
}
