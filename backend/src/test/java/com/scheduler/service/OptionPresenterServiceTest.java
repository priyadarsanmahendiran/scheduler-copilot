package com.scheduler.service;

import com.scheduler.client.AnthropicClient;
import com.scheduler.model.Job;
import com.scheduler.model.Schedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionPresenterServiceTest {

    @Mock
    AnthropicClient anthropicClient;

    @InjectMocks
    OptionPresenterService service;

    @Test
    void presentOptions_returnsLlmResponse() {
        String llmResponse = "OPTION A (Fast): Jobs distributed evenly.\nOPTION B (Cheap): All jobs on M2.";
        when(anthropicClient.sendMessage(anyString())).thenReturn(llmResponse);

        Schedule original = schedule(Map.of(
                "M1", List.of(new Job("J1", "M1", 30)),
                "M2", List.of(new Job("J2", "M2", 60))));
        Schedule timeOpt = schedule(Map.of("M2", List.of(new Job("J2", "M2", 60), new Job("J1", "M1", 30))));
        Schedule costOpt = schedule(Map.of("M2", List.of(new Job("J2", "M2", 60), new Job("J1", "M1", 30))));

        String result = service.presentOptions("M1", original, timeOpt, costOpt);

        assertThat(result).isEqualTo(llmResponse);
    }

    @Test
    void presentOptions_promptContainsFailedMachineId() {
        when(anthropicClient.sendMessage(anyString())).thenReturn("OPTION A (Fast): x\nOPTION B (Cheap): y");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        Schedule original = schedule(Map.of("M1", List.of(new Job("J1", "M1", 30))));
        Schedule timeOpt = schedule(Map.of("M2", List.of(new Job("J1", "M1", 30))));
        Schedule costOpt = schedule(Map.of("M2", List.of(new Job("J1", "M1", 30))));

        service.presentOptions("M1", original, timeOpt, costOpt);

        verify(anthropicClient).sendMessage(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("M1");
        assertThat(promptCaptor.getValue()).contains("TIME-OPTIMAL");
        assertThat(promptCaptor.getValue()).contains("COST-OPTIMAL");
    }

    @Test
    void presentOptions_promptListsAffectedJobs() {
        when(anthropicClient.sendMessage(anyString())).thenReturn("OPTION A (Fast): x\nOPTION B (Cheap): y");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        Schedule original = schedule(Map.of("M1", List.of(
                new Job("J1", "M1", 30),
                new Job("J2", "M1", 45))));
        Schedule timeOpt = schedule(Map.of("M2", List.of(new Job("J1", "M1", 30))));
        Schedule costOpt = schedule(Map.of("M2", List.of(new Job("J1", "M1", 30), new Job("J2", "M1", 45))));

        service.presentOptions("M1", original, timeOpt, costOpt);

        verify(anthropicClient).sendMessage(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("J1");
        assertThat(prompt).contains("J2");
    }

    private Schedule schedule(Map<String, List<Job>> assignments) {
        return new Schedule(assignments);
    }
}
