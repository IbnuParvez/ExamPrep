package com.examprep.api.model;

/** Lifecycle of an AI study package generation job. */
public enum StudyPackStatus {
    QUEUED,
    PROCESSING,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }

    public String label() {
        return switch (this) {
            case QUEUED -> "Queued";
            case PROCESSING -> "Generating";
            case READY -> "Ready";
            case FAILED -> "Generation failed";
        };
    }
}
