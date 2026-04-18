package com.scheduler.model;

import java.util.List;
import java.util.Map;

public class Schedule {
    private Map<String, List<Job>> assignments;

    public Schedule() {}

    public Schedule(Map<String, List<Job>> assignments) {
        this.assignments = assignments;
    }

    public Map<String, List<Job>> getAssignments() { return assignments; }
    public void setAssignments(Map<String, List<Job>> assignments) { this.assignments = assignments; }
}
