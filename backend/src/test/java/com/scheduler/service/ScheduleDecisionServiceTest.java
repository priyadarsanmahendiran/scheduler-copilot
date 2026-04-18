package com.scheduler.service;

import com.scheduler.model.ChoiceResponse;
import com.scheduler.model.ChoiceResult;
import com.scheduler.model.Job;
import com.scheduler.model.Schedule;
import com.scheduler.model.ScheduleDecisionResponse;
import com.scheduler.model.ScheduleMetrics;
import com.scheduler.model.ScheduleWithMetrics;
import com.scheduler.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleDecisionServiceTest {

    @Mock CpSatSchedulingService cpSatSchedulingService;
    @Mock ClaudeAgentService claudeAgentService;
    @Mock ChoiceInterpreterService choiceInterpreterService;
    @Mock InMemoryStore store;

    ScheduleDecisionService service;

    private final Schedule original = new Schedule(Map.of(
            "M1", List.of(new Job("J1", "M1", 30)),
            "M2", List.of(new Job("J2", "M2", 60))));
    private final Schedule timeOptSchedule = new Schedule(Map.of(
            "M2", List.of(new Job("J2", "M2", 60), new Job("J1", "M1", 30))));
    private final Schedule costOptSchedule = new Schedule(Map.of(
            "M2", List.of(new Job("J2", "M2", 60), new Job("J1", "M1", 30))));

    private final ScheduleWithMetrics timeOpt = new ScheduleWithMetrics(
            timeOptSchedule, new ScheduleMetrics(90, 1, Map.of("M2", 90)));
    private final ScheduleWithMetrics costOpt = new ScheduleWithMetrics(
            costOptSchedule, new ScheduleMetrics(90, 1, Map.of("M2", 90)));

    @BeforeEach
    void setUp() {
        service = new ScheduleDecisionService(
                cpSatSchedulingService, claudeAgentService, choiceInterpreterService, store);
    }

    @Test
    void processFailure_returnsSessionWithOptions() {
        when(store.getCurrentSchedule()).thenReturn(original);
        when(cpSatSchedulingService.solveTimeOptimal("M1")).thenReturn(timeOpt);
        when(cpSatSchedulingService.solveCostOptimal("M1")).thenReturn(costOpt);
        String analysis = "SITUATION: M1 failed.\n\nOPTION A (Fast): Jobs evenly spread.\n\nOPTION B (Cheap): All on M2.\n\nMY RECOMMENDATION: Option B.";
        when(claudeAgentService.analyze(eq("M1"), any(), any())).thenReturn(analysis);

        ScheduleDecisionResponse response = service.processFailure("M1");

        assertThat(response.getSessionId()).isNotBlank();
        assertThat(response.getClaudeAnalysis()).isEqualTo(analysis);
        assertThat(response.getOptionAText()).contains("evenly spread");
        assertThat(response.getOptionBText()).contains("All on M2");
        assertThat(response.getOptionAMetrics()).isNotNull();
        assertThat(response.getOptionBMetrics()).isNotNull();
        assertThat(response.getExpiresAt()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void processChoice_appliesTimeOptimalSchedule() {
        when(store.getCurrentSchedule()).thenReturn(original);
        when(cpSatSchedulingService.solveTimeOptimal("M1")).thenReturn(timeOpt);
        when(cpSatSchedulingService.solveCostOptimal("M1")).thenReturn(costOpt);
        when(claudeAgentService.analyze(anyString(), any(), any()))
                .thenReturn("OPTION A (Fast): Fast option.\nOPTION B (Cheap): Cheap option.");

        ScheduleDecisionResponse decision = service.processFailure("M1");

        when(choiceInterpreterService.interpretChoice(anyString(), anyString(), anyString()))
                .thenReturn(ChoiceResult.of("time", 0.95));

        ChoiceResponse result = service.processChoice(decision.getSessionId(), "go faster");

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getAppliedSchedule()).isSameAs(timeOptSchedule);
        verify(store).setCurrentSchedule(timeOptSchedule);
    }

    @Test
    void processChoice_appliesCostOptimalSchedule() {
        when(store.getCurrentSchedule()).thenReturn(original);
        when(cpSatSchedulingService.solveTimeOptimal("M1")).thenReturn(timeOpt);
        when(cpSatSchedulingService.solveCostOptimal("M1")).thenReturn(costOpt);
        when(claudeAgentService.analyze(anyString(), any(), any()))
                .thenReturn("OPTION A (Fast): Fast option.\nOPTION B (Cheap): Cheap option.");

        ScheduleDecisionResponse decision = service.processFailure("M1");

        when(choiceInterpreterService.interpretChoice(anyString(), anyString(), anyString()))
                .thenReturn(ChoiceResult.of("cost", 0.88));

        ChoiceResponse result = service.processChoice(decision.getSessionId(), "minimize costs");

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getAppliedSchedule()).isSameAs(costOptSchedule);
        verify(store).setCurrentSchedule(costOptSchedule);
    }

    @Test
    void processChoice_returnsClarificationWhenLlmUnsure() {
        when(store.getCurrentSchedule()).thenReturn(original);
        when(cpSatSchedulingService.solveTimeOptimal("M1")).thenReturn(timeOpt);
        when(cpSatSchedulingService.solveCostOptimal("M1")).thenReturn(costOpt);
        when(claudeAgentService.analyze(anyString(), any(), any()))
                .thenReturn("OPTION A (Fast): Fast option.\nOPTION B (Cheap): Cheap option.");

        ScheduleDecisionResponse decision = service.processFailure("M1");

        when(choiceInterpreterService.interpretChoice(anyString(), anyString(), anyString()))
                .thenReturn(ChoiceResult.clarification("Did you mean Option A or B?"));

        ChoiceResponse result = service.processChoice(decision.getSessionId(), "umm");

        assertThat(result.isApplied()).isFalse();
        assertThat(result.isNeedsClarification()).isTrue();
        assertThat(result.getClarificationMessage()).isNotBlank();
    }

    @Test
    void processChoice_returnsErrorForUnknownSession() {
        ChoiceResponse result = service.processChoice("non-existent-id", "go faster");

        assertThat(result.isNeedsClarification()).isTrue();
        assertThat(result.getClarificationMessage()).contains("not found");
    }

    @Test
    void processChoice_sessionIsRemovedAfterChoiceApplied() {
        when(store.getCurrentSchedule()).thenReturn(original);
        when(cpSatSchedulingService.solveTimeOptimal("M1")).thenReturn(timeOpt);
        when(cpSatSchedulingService.solveCostOptimal("M1")).thenReturn(costOpt);
        when(claudeAgentService.analyze(anyString(), any(), any()))
                .thenReturn("OPTION A (Fast): Fast option.\nOPTION B (Cheap): Cheap option.");

        ScheduleDecisionResponse decision = service.processFailure("M1");

        when(choiceInterpreterService.interpretChoice(anyString(), anyString(), anyString()))
                .thenReturn(ChoiceResult.of("time", 0.9));

        service.processChoice(decision.getSessionId(), "go faster");

        ChoiceResponse second = service.processChoice(decision.getSessionId(), "go faster");
        assertThat(second.isNeedsClarification()).isTrue();
        assertThat(second.getClarificationMessage()).contains("not found");
    }
}
