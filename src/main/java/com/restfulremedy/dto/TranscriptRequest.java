package com.restfulremedy.dto;

import jakarta.validation.constraints.NotBlank;

public class TranscriptRequest {

    @NotBlank(message = "Transcript text is required")
    private String transcript;

    public TranscriptRequest() {}

    public TranscriptRequest(String transcript) {
        this.transcript = transcript;
    }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
}
