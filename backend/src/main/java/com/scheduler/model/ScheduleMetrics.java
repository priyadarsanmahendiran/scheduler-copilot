package com.scheduler.model;

import java.util.Map;

public class ScheduleMetrics {
    private int makespan;
    private int disruptedCount;
    private Map<String, Integer> machineLoads;

    public ScheduleMetrics() {}

    public ScheduleMetrics(int makespan, int disruptedCount, Map<String, Integer> machineLoads) {
        this.makespan = makespan;
        this.disruptedCount = disruptedCount;
        this.machineLoads = machineLoads;
    }

    public int getMakespan() { return makespan; }
    public void setMakespan(int makespan) { this.makespan = makespan; }

    public int getDisruptedCount() { return disruptedCount; }
    public void setDisruptedCount(int disruptedCount) { this.disruptedCount = disruptedCount; }

    public Map<String, Integer> getMachineLoads() { return machineLoads; }
    public void setMachineLoads(Map<String, Integer> machineLoads) { this.machineLoads = machineLoads; }
}
