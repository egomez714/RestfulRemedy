package com.restfulremedy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcript_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscriptRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "raw_transcript", columnDefinition = "TEXT", nullable = false)
    private String rawTranscript;

    // JdbcTypeCode(LONGVARCHAR) tells Hibernate to use TEXT in Postgres
    // instead of the default VARCHAR(255) which would truncate large JSON payloads.
    @Column(name = "claude_json", nullable = false)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String claudeJson;

    @Column(name = "fhir_json")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String fhirJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
