package com.examprep.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.examprep.api.model.DocumentStatus;

/** Lightweight document row for listings and the dashboard. */
public record DocumentSummaryDto(
        UUID id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        DocumentStatus status,
        Instant createdAt,
        int chunkCount
) {
    public boolean ready() {
        return status == DocumentStatus.READY;
    }

    public String sizeLabel() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        }
        if (fileSizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", fileSizeBytes / 1024.0);
        }
        return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
    }
}
