package com.examprep.document.service;

/**
 * A chunk ready to be persisted.
 */
public record ChunkDraft(String referenceId, String locationLabel, String heading, String text) {
}
