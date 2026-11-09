package com.examprep.api.document;

/**
 * Lifecycle of an uploaded document. Stored as a string column so that values remain stable.
 */
public enum DocumentProcessingStatus {
    UPLOADED("Uploaded"),
    PROCESSING("Processing"),
    READY("Ready"),
    FAILED("Failed");

    private final String label;

    DocumentProcessingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
