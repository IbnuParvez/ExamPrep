package com.examprep.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.examprep.api.model.QuestionType;

/**
 * A live examination view. Correct answers and explanations are intentionally absent so they
 * cannot reach the browser before submission.
 */
public record ExamSessionDto(
        UUID attemptId,
        UUID studyPackId,
        String studyPackTitle,
        int durationSeconds,
        int timeTakenSeconds,
        Instant startedAt,
        List<Question> questions
) {
    public record Question(UUID questionId, int position, QuestionType type, String question, List<String> options) {
    }
}
