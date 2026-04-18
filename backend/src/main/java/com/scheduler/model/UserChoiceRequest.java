package com.scheduler.model;

public class UserChoiceRequest {
    private String sessionId;
    private String userMessage;

    public UserChoiceRequest() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
}
