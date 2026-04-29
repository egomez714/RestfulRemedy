package com.restfulremedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfulremedy.config.AnthropicConfig;
import com.restfulremedy.prompts.TranscriptPrompts;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TranscriptService {

    private final RestClient anthropicRestClient;
    private final AnthropicConfig config;
    private final ObjectMapper objectMapper;

    public TranscriptService(RestClient anthropicRestClient, AnthropicConfig config, ObjectMapper objectMapper) {
        this.anthropicRestClient = anthropicRestClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> analyzeTranscript(String transcript) {
        log.info("Analyzing transcript of length {}", transcript.length());

        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "max_tokens", 1024,
                "system", TranscriptPrompts.EXTRACT_CLINICAL_DATA,
                "messages", List.of(
                        Map.of("role", "user", "content", transcript)
                )
        );

        Map response = anthropicRestClient.post()
                .uri("/messages")
                .body(requestBody)
                .retrieve()
                .body(Map.class);
        // Extract text from Claude's response
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Empty response from Claude API");
        }

        String jsonText = (String) content.get(0).get("text");
        if (jsonText == null || jsonText.isBlank()) {
            throw new RuntimeException("Claude returned empty text content");
        }

        try {
            Map<String, Object> result = objectMapper.readValue(jsonText, Map.class);
            log.info("Successfully received structured response from Claude");
            return result;
        } catch (Exception e) {
            log.error("Failed to analyze transcript",e);
            throw new RuntimeException("Claude returned invalid JSON: " + jsonText, e);
        }
    }
}
