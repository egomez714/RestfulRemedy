package com.restfulremedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfulremedy.dto.TranscriptRequest;
import com.restfulremedy.service.TranscriptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TranscriptController.class)
class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranscriptService transcriptService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthCheck_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("RestfulRemedy"));
    }

    @Test
    void analyzeTranscript_returnsResult() throws Exception {
        Map<String, Object> mockResult = Map.of(
                "medications", List.of(Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377")),
                "diagnoses", List.of(Map.of("description", "Hypertension", "icd10_code", "I10")),
                "summary", "Routine follow-up for blood pressure management"
        );
        when(transcriptService.analyzeTranscript(anyString())).thenReturn(mockResult);

        TranscriptRequest request = new TranscriptRequest("Patient has high blood pressure, prescribed Lisinopril 10mg");

        mockMvc.perform(post("/api/transcripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medications[0].name").value("Lisinopril"))
                .andExpect(jsonPath("$.diagnoses[0].icd10_code").value("I10"));
    }

    @Test
    void analyzeTranscript_blankTranscript_returnsBadRequest() throws Exception {
        TranscriptRequest request = new TranscriptRequest("   ");

        mockMvc.perform(post("/api/transcripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Transcript text is required"));
    }

    @Test
    void analyzeTranscript_missingTranscript_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transcripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeTranscript_serviceError_returns500() throws Exception {
        when(transcriptService.analyzeTranscript(anyString()))
                .thenThrow(new RuntimeException("Claude returned invalid JSON"));

        TranscriptRequest request = new TranscriptRequest("Some transcript text");

        mockMvc.perform(post("/api/transcripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}
