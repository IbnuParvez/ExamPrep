package com.examprep.api.model;

/** Supported practice and examination question types. */
public enum QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER;

    public String label() {
        return switch (this) {
            case MULTIPLE_CHOICE -> "Multiple choice";
            case TRUE_FALSE -> "True or false";
            case SHORT_ANSWER -> "Short answer";
        };
    }

    public boolean isAutoScored() {
        return this != SHORT_ANSWER;
    }
}
