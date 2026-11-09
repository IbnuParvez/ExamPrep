package com.examprep.api.study;

/**
 * Supported practice question formats.
 */
public enum QuestionType {
    MULTIPLE_CHOICE("Multiple choice"),
    TRUE_FALSE("True / false"),
    SHORT_ANSWER("Short answer");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean supportsAutomaticScoring() {
        return this == MULTIPLE_CHOICE || this == TRUE_FALSE;
    }
}
