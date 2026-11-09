package com.examprep.api.dto;

import java.util.UUID;

import com.examprep.api.model.QuestionType;

/** Feedback for a practice question, returned only after the student asks for it. */
public record PracticeAnswerDto(
        UUID questionId,
        QuestionType type,
        String correctAnswer,
        String explanation,
        String sourceRef
) {
}
