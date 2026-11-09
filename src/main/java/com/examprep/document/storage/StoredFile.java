package com.examprep.document.storage;

/**
 * Result of writing an upload to private storage.
 */
public record StoredFile(String storageKey, long sizeBytes) {
}
