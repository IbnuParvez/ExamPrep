package com.examprep.api.model;

/** Lifecycle of an uploaded document's extraction pipeline. */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }

    public String label() {
        return switch (this) {
            case UPLOADED -> "Queued for extraction";
            case PROCESSING -> "Extracting text";
            case READY -> "Ready for study";
            case FAILED -> "Extraction failed";
        };
    }
}
