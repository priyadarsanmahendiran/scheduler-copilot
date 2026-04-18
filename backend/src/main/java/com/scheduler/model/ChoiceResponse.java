package com.scheduler.model;

public class ChoiceResponse {
    private boolean applied;
    private Schedule appliedSchedule;
    private boolean needsClarification;
    private String clarificationMessage;

    public static ChoiceResponse applied(Schedule schedule) {
        ChoiceResponse r = new ChoiceResponse();
        r.applied = true;
        r.appliedSchedule = schedule;
        return r;
    }

    public static ChoiceResponse needsClarification(String message) {
        ChoiceResponse r = new ChoiceResponse();
        r.needsClarification = true;
        r.clarificationMessage = message;
        return r;
    }

    public boolean isApplied() { return applied; }
    public Schedule getAppliedSchedule() { return appliedSchedule; }
    public boolean isNeedsClarification() { return needsClarification; }
    public String getClarificationMessage() { return clarificationMessage; }
}
