package com.examprep.api.study;

import java.time.LocalDate;

/**
 * Student choices for a generation run. Null numeric values fall back to configured defaults.
 */
public record StudyPreferences(String language,
                               String academicLevel,
                               LocalDate examDate,
                               Integer questionCount,
                               Integer flashcardCount,
                               Integer examDurationMinutes) {

    public static StudyPreferences defaults() {
        return new StudyPreferences("English", "Undergraduate", null, null, null, null);
    }
}
