package com.scheduler.config;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.store.InMemoryStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataInitializer {
    private final InMemoryStore store;

    public DataInitializer(InMemoryStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        long now = System.currentTimeMillis();

        store.getMachines().put("M1", new Machine("M1", "RUNNING", now));
        store.getMachines().put("M2", new Machine("M2", "RUNNING", now));
        store.getMachines().put("M3", new Machine("M3", "RUNNING", now));

        Job j1 = new Job("J1", "M1", 30);
        Job j2 = new Job("J2", "M1", 45);
        Job j3 = new Job("J3", "M2", 60);
        Job j4 = new Job("J4", "M2", 20);
        Job j5 = new Job("J5", "M3", 50);

        for (Job job : List.of(j1, j2, j3, j4, j5)) {
            store.getJobs().put(job.getId(), job);
        }

        store.setCurrentSchedule(new Schedule(Map.of(
                "M1", List.of(j1, j2),
                "M2", List.of(j3, j4),
                "M3", List.of(j5)
        )));
    }
}
