package com.examprep.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Completed examination summary for dashboards and progress views. */
public record QuizAttemptSummaryDto(
        UUID attemptId,
        UUID studyPackId,
        String studyPackTitle,
        int score,
        int totalQuestions,
        int percentage,
        int timeTakenSeconds,
        Instant submittedAt
) {
}
