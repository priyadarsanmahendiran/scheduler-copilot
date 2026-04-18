package com.scheduler.model;

public class ChoiceResult {
    private String choice;
    private double confidence;
    private boolean needsClarification;
    private String clarificationMessage;

    private ChoiceResult() {}

    public static ChoiceResult of(String choice, double confidence) {
        ChoiceResult r = new ChoiceResult();
        r.choice = choice;
        r.confidence = confidence;
        r.needsClarification = false;
        return r;
    }

    public static ChoiceResult clarification(String message) {
        ChoiceResult r = new ChoiceResult();
        r.needsClarification = true;
        r.clarificationMessage = message;
        return r;
    }

    public String getChoice() { return choice; }
    public double getConfidence() { return confidence; }
    public boolean isNeedsClarification() { return needsClarification; }
    public String getClarificationMessage() { return clarificationMessage; }
}
