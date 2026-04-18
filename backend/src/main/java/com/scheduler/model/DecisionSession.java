package com.scheduler.model;

public class DecisionSession {
    private String sessionId;
    private SchedulePair schedulePair;
    private String claudeAnalysis;
    private String optionAText;
    private String optionBText;
    private ScheduleMetrics optionAMetrics;
    private ScheduleMetrics optionBMetrics;
    private long createdAt;

    public DecisionSession() {}

    public DecisionSession(String sessionId, SchedulePair schedulePair,
                           String claudeAnalysis, String optionAText, String optionBText,
                           ScheduleMetrics optionAMetrics, ScheduleMetrics optionBMetrics) {
        this.sessionId = sessionId;
        this.schedulePair = schedulePair;
        this.claudeAnalysis = claudeAnalysis;
        this.optionAText = optionAText;
        this.optionBText = optionBText;
        this.optionAMetrics = optionAMetrics;
        this.optionBMetrics = optionBMetrics;
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
}
