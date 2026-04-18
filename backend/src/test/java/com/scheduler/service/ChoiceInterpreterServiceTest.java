package com.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.client.AnthropicClient;
import com.scheduler.model.ChoiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoiceInterpreterServiceTest {

    @Mock
    AnthropicClient anthropicClient;

    ChoiceInterpreterService service;

    @BeforeEach
    void setUp() {
        service = new ChoiceInterpreterService(anthropicClient, new ObjectMapper());
    }

    @Test
    void interpretChoice_parsesTimeChoice() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("{\"choice\": \"time\", \"confidence\": 0.95}");

        ChoiceResult result = service.interpretChoice("go with the faster one", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isFalse();
        assertThat(result.getChoice()).isEqualTo("time");
        assertThat(result.getConfidence()).isEqualTo(0.95);
    }

    @Test
    void interpretChoice_parsesCostChoice() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("{\"choice\": \"cost\", \"confidence\": 0.88}");

        ChoiceResult result = service.interpretChoice("pick option B", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isFalse();
        assertThat(result.getChoice()).isEqualTo("cost");
        assertThat(result.getConfidence()).isEqualTo(0.88);
    }

    @Test
    void interpretChoice_requestsClarificationWhenUnclear() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("{\"choice\": \"unclear\", \"confidence\": 0.0}");

        ChoiceResult result = service.interpretChoice("hmm I'm not sure", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isTrue();
        assertThat(result.getClarificationMessage()).isNotBlank();
    }

    @Test
    void interpretChoice_requestsClarificationWhenLowConfidence() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("{\"choice\": \"time\", \"confidence\": 0.4}");

        ChoiceResult result = service.interpretChoice("maybe the first one?", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isTrue();
    }

    @Test
    void interpretChoice_handlesMarkdownWrappedJson() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("```json\n{\"choice\": \"cost\", \"confidence\": 0.9}\n```");

        ChoiceResult result = service.interpretChoice("minimize costs", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isFalse();
        assertThat(result.getChoice()).isEqualTo("cost");
    }

    @Test
    void interpretChoice_requestsClarificationWhenMalformedJson() {
        when(anthropicClient.sendMessage(anyString()))
                .thenReturn("I cannot determine the user's intent from this input.");

        ChoiceResult result = service.interpretChoice("whatever", "Option A text", "Option B text");

        assertThat(result.isNeedsClarification()).isTrue();
        assertThat(result.getClarificationMessage()).contains("Option A");
    }
}
