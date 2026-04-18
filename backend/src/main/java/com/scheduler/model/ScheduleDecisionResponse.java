package com.scheduler.model;

public class ScheduleDecisionResponse {
    private String sessionId;
    private String claudeAnalysis;
    private String optionAText;
    private String optionBText;
    private Schedule optionASchedule;
    private Schedule optionBSchedule;
    private ScheduleMetrics optionAMetrics;
    private ScheduleMetrics optionBMetrics;
    private long expiresAt;

    public ScheduleDecisionResponse() {}

    public ScheduleDecisionResponse(String sessionId, String claudeAnalysis,
                                    String optionAText, String optionBText,
                                    Schedule optionASchedule, Schedule optionBSchedule,
                                    ScheduleMetrics optionAMetrics, ScheduleMetrics optionBMetrics,
                                    long expiresAt) {
        this.sessionId = sessionId;
        this.claudeAnalysis = claudeAnalysis;
        this.optionAText = optionAText;
        this.optionBText = optionBText;
        this.optionASchedule = optionASchedule;
        this.optionBSchedule = optionBSchedule;
        this.optionAMetrics = optionAMetrics;
        this.optionBMetrics = optionBMetrics;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getClaudeAnalysis() { return claudeAnalysis; }
    public void setClaudeAnalysis(String claudeAnalysis) { this.claudeAnalysis = claudeAnalysis; }

    public String getOptionAText() { return optionAText; }
    public void setOptionAText(String optionAText) { this.optionAText = optionAText; }

    public String getOptionBText() { return optionBText; }
    public void setOptionBText(String optionBText) { this.optionBText = optionBText; }

    public Schedule getOptionASchedule() { return optionASchedule; }
    public void setOptionASchedule(Schedule optionASchedule) { this.optionASchedule = optionASchedule; }

    public Schedule getOptionBSchedule() { return optionBSchedule; }
    public void setOptionBSchedule(Schedule optionBSchedule) { this.optionBSchedule = optionBSchedule; }

    public ScheduleMetrics getOptionAMetrics() { return optionAMetrics; }
    public void setOptionAMetrics(ScheduleMetrics optionAMetrics) { this.optionAMetrics = optionAMetrics; }

    public ScheduleMetrics getOptionBMetrics() { return optionBMetrics; }
    public void setOptionBMetrics(ScheduleMetrics optionBMetrics) { this.optionBMetrics = optionBMetrics; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}
