package com.examprep.api.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe processing status. {@code failureReason} never contains document contents.
 */
public record DocumentStatusDto(UUID documentId,
                                String originalFilename,
                                String contentType,
                                long sizeBytes,
                                DocumentProcessingStatus status,
                                int chunkCount,
                                Integer pageCount,
                                Integer characterCount,
                                String failureReason,
                                Instant createdAt,
                                Instant updatedAt) {
}
