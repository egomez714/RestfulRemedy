package com.restfulremedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfulremedy.config.AnthropicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private AnthropicConfig config;

    private TranscriptService transcriptService;

    @BeforeEach
    void setUp() {
        transcriptService = new TranscriptService(restClient, config, new ObjectMapper());
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

        // Mock the RestClient chain
        RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);
        when(config.getModel()).thenReturn("claude-sonnet-4-20250514");

        Map<String, Object> result = transcriptService.analyzeTranscript("Patient has high blood pressure");

        assertNotNull(result);
        assertTrue(result.containsKey("medications"));
        assertTrue(result.containsKey("diagnoses"));
        assertTrue(result.containsKey("summary"));
    }

    @Test
    void analyzeTranscript_emptyContent_throwsException() {
        Map<String, Object> apiResponse = Map.of("content", List.of());

        RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);
        when(config.getModel()).thenReturn("claude-sonnet-4-20250514");

        assertThrows(RuntimeException.class, () ->
                transcriptService.analyzeTranscript("Some transcript"));
    }

    @Test
    void analyzeTranscript_invalidJson_throwsException() {
        Map<String, Object> apiResponse = Map.of(
                "content", List.of(Map.of("type", "text", "text", "not valid json!!!"))
        );

        RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(apiResponse);
        when(config.getModel()).thenReturn("claude-sonnet-4-20250514");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                transcriptService.analyzeTranscript("Some transcript"));
        assertTrue(ex.getMessage().contains("invalid JSON"));
    }
}
