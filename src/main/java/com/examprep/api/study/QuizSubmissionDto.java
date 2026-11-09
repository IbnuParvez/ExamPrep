package com.examprep.api.study;

import java.util.List;

/**
 * Submitted mock examination. The client only ever sends chosen option text; scoring happens
 * on the server against the persisted answer key.
 */
public record QuizSubmissionDto(List<QuizAnswerSubmission> answers, int timeTakenSeconds) {

    public QuizSubmissionDto {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }
}
