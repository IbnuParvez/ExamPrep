package com.examprep.api.study;

/**
 * Question difficulty reported by the AI model, normalised to a small stable set.
 */
public enum Difficulty {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Difficulty fromModelValue(String value) {
        if (value == null) {
            return MEDIUM;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (Difficulty difficulty : values()) {
            if (difficulty.name().equals(normalized)) {
                return difficulty;
            }
        }
        return MEDIUM;
    }
}
