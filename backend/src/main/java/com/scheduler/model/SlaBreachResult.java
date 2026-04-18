package com.scheduler.model;

import java.util.List;

public class SlaBreachResult {
    private int breachCount;
    private List<String> breachedJobIds;

    public SlaBreachResult() {}

    public SlaBreachResult(int breachCount, List<String> breachedJobIds) {
        this.breachCount = breachCount;
        this.breachedJobIds = breachedJobIds;
    }

    public int getBreachCount() { return breachCount; }
    public void setBreachCount(int breachCount) { this.breachCount = breachCount; }

    public List<String> getBreachedJobIds() { return breachedJobIds; }
    public void setBreachedJobIds(List<String> breachedJobIds) { this.breachedJobIds = breachedJobIds; }
}
