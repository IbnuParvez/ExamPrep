package com.examprep.api.dto;

import java.time.LocalDate;

/** Student choices that steer study package generation. */
public record StudyPreferences(
        String language,
        String academicLevel,
        LocalDate examDate,
        int questionCount,
        int flashcardCount,
        int examQuestionCount,
        int examDurationMinutes
) {
    public static StudyPreferences defaults() {
        return new StudyPreferences("English", "Undergraduate", null, 12, 16, 10, 15);
    }
}
