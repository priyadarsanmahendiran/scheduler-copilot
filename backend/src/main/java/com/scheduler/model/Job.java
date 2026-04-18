package com.scheduler.model;

public class Job {
    private String id;
    private String machineId;
    private int duration;
    private int deadline; // max allowed completion time in minutes from T=0; 0 = no deadline

    public Job() {}

    public Job(String id, String machineId, int duration) {
        this.id = id;
        this.machineId = machineId;
        this.duration = duration;
    }

    public Job(String id, String machineId, int duration, int deadline) {
        this(id, machineId, duration);
        this.deadline = deadline;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public int getDeadline() { return deadline; }
    public void setDeadline(int deadline) { this.deadline = deadline; }
}
