package com.examprep.api.document;

import java.util.List;
import java.util.UUID;

/**
 * Extracted material handed to the AI layer. The owner identifier is carried so callers can
 * assert ownership before the context leaves the document module.
 */
public record DocumentContext(UUID documentId,
                              UUID ownerId,
                              String originalFilename,
                              List<DocumentChunkDto> chunks) {
}
