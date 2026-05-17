package com.restfulremedy.repository;

import com.restfulremedy.entity.TranscriptRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<TranscriptRecord, UUID> {
}
