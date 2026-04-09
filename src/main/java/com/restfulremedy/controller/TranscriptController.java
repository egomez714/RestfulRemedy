package com.restfulremedy.controller;

import com.restfulremedy.dto.TranscriptRequest;
import com.restfulremedy.service.FhirService;
import com.restfulremedy.service.TranscriptService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranscriptController {

    private final TranscriptService transcriptService;
    private final FhirService fhirService;

    public TranscriptController(TranscriptService transcriptService, FhirService fhirService) {
        this.transcriptService = transcriptService;
        this.fhirService = fhirService;
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
        return ResponseEntity.ok(result);
    }

    /**
     * Analyzes a transcript and returns a FHIR R4 Bundle.
     * The FHIR content type "application/fhir+json" tells clients
     * this is a standards-compliant FHIR resource.
     */
    @PostMapping("/transcripts/fhir")
    public ResponseEntity<String> analyzeTranscriptFhir(@Valid @RequestBody TranscriptRequest request) {
        Map<String, Object> result = transcriptService.analyzeTranscript(request.getTranscript());
        Bundle bundle = fhirService.buildBundle(result);
        String fhirJson = fhirService.serializeBundle(bundle);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/fhir+json"))
                .body(fhirJson);
    }
}
