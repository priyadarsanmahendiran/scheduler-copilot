package com.scheduler.model;

public class ScheduleWithMetrics {
    private Schedule schedule;
    private ScheduleMetrics metrics;

    public ScheduleWithMetrics() {}

    public ScheduleWithMetrics(Schedule schedule, ScheduleMetrics metrics) {
        this.schedule = schedule;
        this.metrics = metrics;
    }

    public Schedule getSchedule() { return schedule; }
    public void setSchedule(Schedule schedule) { this.schedule = schedule; }

    public ScheduleMetrics getMetrics() { return metrics; }
    public void setMetrics(ScheduleMetrics metrics) { this.metrics = metrics; }
}
