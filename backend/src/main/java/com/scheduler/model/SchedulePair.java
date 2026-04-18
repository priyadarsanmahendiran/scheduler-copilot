package com.scheduler.model;

public class SchedulePair {
    private String failedMachineId;
    private Schedule original;
    private Schedule timeOptimal;
    private Schedule costOptimal;

    public SchedulePair() {}

    public SchedulePair(String failedMachineId, Schedule original, Schedule timeOptimal, Schedule costOptimal) {
        this.failedMachineId = failedMachineId;
        this.original = original;
        this.timeOptimal = timeOptimal;
        this.costOptimal = costOptimal;
    }

    public String getFailedMachineId() { return failedMachineId; }
    public void setFailedMachineId(String failedMachineId) { this.failedMachineId = failedMachineId; }

    public Schedule getOriginal() { return original; }
    public void setOriginal(Schedule original) { this.original = original; }

    public Schedule getTimeOptimal() { return timeOptimal; }
    public void setTimeOptimal(Schedule timeOptimal) { this.timeOptimal = timeOptimal; }

    public Schedule getCostOptimal() { return costOptimal; }
    public void setCostOptimal(Schedule costOptimal) { this.costOptimal = costOptimal; }
}
