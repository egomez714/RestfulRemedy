package com.restfulremedy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfulremedy.dto.TranscriptRequest;
import com.restfulremedy.entity.TranscriptRecord;
import com.restfulremedy.repository.TranscriptRepository;
import com.restfulremedy.service.FhirService;
import com.restfulremedy.service.TranscriptService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class TranscriptController {

    private final TranscriptService transcriptService;
    private final FhirService fhirService;
    private final TranscriptRepository transcriptRepository;
    private final ObjectMapper objectMapper;

    public TranscriptController(TranscriptService transcriptService,
                                FhirService fhirService,
                                TranscriptRepository transcriptRepository,
                                ObjectMapper objectMapper) {
        this.transcriptService = transcriptService;
        this.fhirService = fhirService;
        this.transcriptRepository = transcriptRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "RestfulRemedy"
        ));
    }

    @PostMapping("/transcripts")
    public ResponseEntity<?> analyzeTranscript(@Valid @RequestBody TranscriptRequest request) {
        Map<String, Object> result = transcriptService.analyzeTranscript(request.getTranscript());
        persist(request.getTranscript(), result, null);
        return ResponseEntity.ok(result);
    }

    /**
     * Analyzes a transcript and returns a FHIR R4 Bundle.
     */
    @PostMapping("/transcripts/fhir")
    public ResponseEntity<String> analyzeTranscriptFhir(@Valid @RequestBody TranscriptRequest request) {
        Map<String, Object> result = transcriptService.analyzeTranscript(request.getTranscript());
        Bundle bundle = fhirService.buildBundle(result);
        fhirService.validate(bundle);
        String fhirJson = fhirService.serializeBundle(bundle);
        persist(request.getTranscript(), result, fhirJson);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/fhir+json"))
                .body(fhirJson);
    }

    private void persist(String transcript, Map<String, Object> claudeResult, String fhirJson) {
        try {
            String claudeJson = objectMapper.writeValueAsString(claudeResult);
            transcriptRepository.save(TranscriptRecord.builder()
                    .rawTranscript(transcript)
                    .claudeJson(claudeJson)
                    .fhirJson(fhirJson)
                    .build());
        } catch (JsonProcessingException e) {
            // Persistence is a side effect — log and move on rather than fail the request.
            log.warn("Failed to serialize Claude result for persistence", e);
        }
    }
}
