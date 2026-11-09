package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

public record ExamSummaryDto(UUID attemptId,
                             UUID studyPackId,
                             String studyPackTitle,
                             int score,
                             int totalQuestions,
                             int timeTakenSeconds,
                             Instant submittedAt) {
}
