package com.examprep.api.dto;

import java.util.List;
import java.util.UUID;

/** Submitted mock examination answers. */
public record QuizSubmissionDto(
        UUID attemptId,
        int timeTakenSeconds,
        boolean confirmed,
        List<Answer> answers
) {
    public record Answer(UUID questionId, Integer selectedOptionIndex, Boolean booleanAnswer) {
    }
}
