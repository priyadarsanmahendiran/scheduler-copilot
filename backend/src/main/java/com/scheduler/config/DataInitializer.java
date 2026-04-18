package com.scheduler.config;

import com.scheduler.model.Job;
import com.scheduler.model.Machine;
import com.scheduler.model.Schedule;
import com.scheduler.store.InMemoryStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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

        for (int i = 1; i <= 15; i++) {
            String id = "M" + i;
            store.getMachines().put(id, new Machine(id, "RUNNING", now));
        }

        // Deadlines are tight enough to breach under cost-optimal redistribution
        // (jobs pile onto fewer machines) but safe in the current balanced schedule.
        //
        // M1  → J1(25min, dl=80),  J2(35min, dl=100)   finish: 25, 60
        // M2  → J3(40min, dl=90),  J4(20min, dl=120)   finish: 40, 60
        // M3  → J5(50min, dl=130), J6(30min, dl=85)    finish: 50, 80
        // M4  → J7(45min, dl=110), J8(15min, dl=70)    finish: 45, 60
        // M5  → J9(60min, dl=140)                      finish: 60
        // M6  → J10(25min, dl=65), J11(35min, dl=105)  finish: 25, 60
        // M7  → J12(40min, dl=95)                      finish: 40
        // M8  → J13(55min, dl=120),J14(20min, dl=80)   finish: 55, 75
        // M9  → J15(30min, dl=75)                      finish: 30
        // M10 → J16(45min, dl=100),J17(25min, dl=85)   finish: 45, 70
        // M11 → J18(50min, dl=110)                     finish: 50
        // M12 → J19(35min, dl=90), J20(30min, dl=125)  finish: 35, 65
        // M13 → J21(40min, dl=100)                     finish: 40
        // M14 → J22(60min, dl=150)                     finish: 60
        // M15 → J23(20min, dl=60), J24(45min, dl=115)  finish: 20, 65

        Job j1  = new Job("J1",  "M1",  25,  80);
        Job j2  = new Job("J2",  "M1",  35, 100);
        Job j3  = new Job("J3",  "M2",  40,  90);
        Job j4  = new Job("J4",  "M2",  20, 120);
        Job j5  = new Job("J5",  "M3",  50, 130);
        Job j6  = new Job("J6",  "M3",  30,  85);
        Job j7  = new Job("J7",  "M4",  45, 110);
        Job j8  = new Job("J8",  "M4",  15,  70);
        Job j9  = new Job("J9",  "M5",  60, 140);
        Job j10 = new Job("J10", "M6",  25,  65);
        Job j11 = new Job("J11", "M6",  35, 105);
        Job j12 = new Job("J12", "M7",  40,  95);
        Job j13 = new Job("J13", "M8",  55, 120);
        Job j14 = new Job("J14", "M8",  20,  80);
        Job j15 = new Job("J15", "M9",  30,  75);
        Job j16 = new Job("J16", "M10", 45, 100);
        Job j17 = new Job("J17", "M10", 25,  85);
        Job j18 = new Job("J18", "M11", 50, 110);
        Job j19 = new Job("J19", "M12", 35,  90);
        Job j20 = new Job("J20", "M12", 30, 125);
        Job j21 = new Job("J21", "M13", 40, 100);
        Job j22 = new Job("J22", "M14", 60, 150);
        Job j23 = new Job("J23", "M15", 20,  60);
        Job j24 = new Job("J24", "M15", 45, 115);

        List<Job> allJobs = List.of(
                j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12,
                j13, j14, j15, j16, j17, j18, j19, j20, j21, j22, j23, j24);

        for (Job job : allJobs) {
            store.getJobs().put(job.getId(), job);
        }

        Map<String, List<Job>> assignments = new LinkedHashMap<>();
        assignments.put("M1",  List.of(j1,  j2));
        assignments.put("M2",  List.of(j3,  j4));
        assignments.put("M3",  List.of(j5,  j6));
        assignments.put("M4",  List.of(j7,  j8));
        assignments.put("M5",  List.of(j9));
        assignments.put("M6",  List.of(j10, j11));
        assignments.put("M7",  List.of(j12));
        assignments.put("M8",  List.of(j13, j14));
        assignments.put("M9",  List.of(j15));
        assignments.put("M10", List.of(j16, j17));
        assignments.put("M11", List.of(j18));
        assignments.put("M12", List.of(j19, j20));
        assignments.put("M13", List.of(j21));
        assignments.put("M14", List.of(j22));
        assignments.put("M15", List.of(j23, j24));

        store.setCurrentSchedule(new Schedule(assignments));
    }
}
