package com.scheduler.model;

public class Job {
    private String id;
    private String machineId;
    private int duration;

    public Job() {}

    public Job(String id, String machineId, int duration) {
        this.id = id;
        this.machineId = machineId;
        this.duration = duration;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
}
