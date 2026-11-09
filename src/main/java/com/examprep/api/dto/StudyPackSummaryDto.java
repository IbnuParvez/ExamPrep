package com.examprep.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.examprep.api.model.StudyPackStatus;

/** Lightweight study package row for listings. */
public record StudyPackSummaryDto(
        UUID id,
        UUID documentId,
        String title,
        String documentFilename,
        StudyPackStatus status,
        int flashcardCount,
        int questionCount,
        Instant createdAt
) {
    public boolean ready() {
        return status == StudyPackStatus.READY;
    }
}
