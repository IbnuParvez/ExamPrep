package com.examprep.api.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizResultDto(UUID attemptId,
                            UUID studyPackId,
                            String studyPackTitle,
                            int score,
                            int totalQuestions,
                            int percentage,
                            int timeTakenSeconds,
                            Instant submittedAt,
                            List<QuizResultItemDto> items,
                            List<String> revisionAreas) {
}
