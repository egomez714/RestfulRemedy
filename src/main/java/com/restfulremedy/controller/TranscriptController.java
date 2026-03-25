package com.restfulremedy.controller;

import com.restfulremedy.dto.TranscriptRequest;
import com.restfulremedy.service.TranscriptService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranscriptController {

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
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
}
