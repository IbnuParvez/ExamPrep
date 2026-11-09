package com.examprep.api.study;

import java.util.UUID;

/**
 * Feedback for a single practice question.
 *
 * @param autoGraded  false for short answer questions, which cannot be scored automatically
 * @param correct     only meaningful when {@code autoGraded} is true
 * @param correctAnswer the correct option, or the model answer for short answer questions
 */
public record PracticeAnswerDto(UUID questionId,
                                boolean autoGraded,
                                boolean correct,
                                String correctAnswer,
                                String explanation,
                                String sourceReference) {
}
