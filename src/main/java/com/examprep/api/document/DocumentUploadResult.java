package com.examprep.api.document;

import java.util.UUID;

public record DocumentUploadResult(UUID documentId,
                                   String originalFilename,
                                   DocumentProcessingStatus status,
                                   long sizeBytes) {
}
