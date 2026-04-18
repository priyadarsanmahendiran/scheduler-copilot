package com.scheduler.store;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class InMemoryStore {
    private final Map<String, Machine> machines = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private Schedule currentSchedule;

    public Map<String, Machine> getMachines() { return machines; }
    public Map<String, Job> getJobs() { return jobs; }

    public Schedule getCurrentSchedule() { return currentSchedule; }
    public void setCurrentSchedule(Schedule schedule) { this.currentSchedule = schedule; }
}
