package com.examprep.api.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryDto(UUID documentId,
                                 String originalFilename,
                                 String contentType,
                                 long sizeBytes,
                                 DocumentProcessingStatus status,
                                 Integer pageCount,
                                 String failureReason,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
