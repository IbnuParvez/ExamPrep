package com.examprep.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.examprep.api.model.DocumentStatus;

/**
 * Processing status of a document. {@code errorMessage} is a safe, human-readable explanation and
 * never contains file contents or internal paths.
 */
public record DocumentStatusDto(
        UUID id,
        String originalFilename,
        String contentType,
        DocumentStatus status,
        String errorMessage,
        int chunkCount,
        Integer pageCount,
        Instant createdAt
) {
    public String statusLabel() {
        return status.label();
    }

    public boolean ready() {
        return status == DocumentStatus.READY;
    }

    public boolean failed() {
        return status == DocumentStatus.FAILED;
    }

    public boolean inProgress() {
        return status == DocumentStatus.UPLOADED || status == DocumentStatus.PROCESSING;
    }

    /** Short, human label for the document family (PDF, DOCX, …) rather than a raw MIME type. */
    public String typeLabel() {
        if (originalFilename == null) {
            return "File";
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot + 1).strip() : "";
        return extension.isBlank() ? "File" : extension.toUpperCase(java.util.Locale.ROOT);
    }
}
