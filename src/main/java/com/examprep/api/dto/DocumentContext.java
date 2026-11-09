package com.examprep.api.dto;

import java.util.List;
import java.util.UUID;

/** Extracted document content ready to be handed to the study engine. */
public record DocumentContext(UUID documentId, String originalFilename, String title, List<DocumentChunk> chunks) {

    public int totalChars() {
        return chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }
}
