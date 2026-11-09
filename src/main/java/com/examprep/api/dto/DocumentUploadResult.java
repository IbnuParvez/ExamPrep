package com.examprep.api.dto;

import java.util.UUID;

import com.examprep.api.model.DocumentStatus;

/** Returned immediately after a document is accepted and queued for extraction. */
public record DocumentUploadResult(UUID documentId, String originalFilename, DocumentStatus status) {
}
