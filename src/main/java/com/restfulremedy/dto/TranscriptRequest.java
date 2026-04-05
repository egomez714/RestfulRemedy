package com.restfulremedy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptRequest {

    @NotBlank(message = "Transcript text is required")

    private String transcript;
    
}
