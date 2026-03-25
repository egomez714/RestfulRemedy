package com.restfulremedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfulremedy.config.AnthropicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranscriptServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private AnthropicConfig config;

    private TranscriptService transcriptService;

    private RestClient.RequestBodyUriSpec requestSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        transcriptService = new TranscriptService(restClient, config, new ObjectMapper());

        requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(config.getModel()).thenReturn("claude-sonnet-4-20250514");
    }

    @Test
    void analyzeTranscript_validResponse_returnsStructuredData() {
        String aiJson = """
                {"medications":[{"name":"Lisinopril","dosage":"10mg","rxnorm_code":"104377"}],
                 "diagnoses":[{"description":"Hypertension","icd10_code":"I10"}],
                 "summary":"Follow-up for blood pressure"}""";

        Map<String, Object> apiResponse = Map.of(
                "content", List.of(Map.of("type", "text", "text", aiJson))
        );
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);

        Map<String, Object> result = transcriptService.analyzeTranscript("Patient has high blood pressure");

        assertNotNull(result);
        assertTrue(result.containsKey("medications"));
        assertTrue(result.containsKey("diagnoses"));
        assertTrue(result.containsKey("summary"));
    }

    @Test
    void analyzeTranscript_emptyContent_throwsException() {
        Map<String, Object> apiResponse = Map.of("content", List.of());
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                transcriptService.analyzeTranscript("Some transcript"));
        assertTrue(ex.getMessage().contains("Empty response"));
    }

    @Test
    void analyzeTranscript_invalidJson_throwsException() {
        Map<String, Object> apiResponse = Map.of(
                "content", List.of(Map.of("type", "text", "text", "not valid json!!!"))
        );
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                transcriptService.analyzeTranscript("Some transcript"));
        assertTrue(ex.getMessage().contains("invalid JSON"));
    }
}
