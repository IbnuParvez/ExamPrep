package com.examprep.api.study;

import java.util.UUID;

public record QuizAnswerSubmission(UUID questionId, String answer) {
}
