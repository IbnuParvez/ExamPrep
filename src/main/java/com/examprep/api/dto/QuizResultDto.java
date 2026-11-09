package com.examprep.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.examprep.api.model.QuestionType;

/** Scored mock examination with per-question feedback. */
public record QuizResultDto(
        UUID attemptId,
        UUID studyPackId,
        String studyPackTitle,
        int score,
        int totalQuestions,
        int percentage,
        int timeTakenSeconds,
        Instant submittedAt,
        List<Item> items,
        List<String> revisionAreas
) {
    public record Item(
            int position,
            String question,
            QuestionType type,
            String studentAnswer,
            String correctAnswer,
            boolean correct,
            String explanation,
            String sourceRef
    ) {
    }
}
