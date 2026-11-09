package com.examprep.api.study;

/**
 * Lifecycle of an AI study package.
 */
public enum StudyPackStatus {
    QUEUED("Queued"),
    PROCESSING("Generating"),
    READY("Ready"),
    FAILED("Failed");

    private final String label;

    StudyPackStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
