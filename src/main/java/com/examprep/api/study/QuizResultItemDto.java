package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

public record QuizResultItemDto(UUID questionId,
                                String prompt,
                                QuestionType type,
                                List<String> options,
                                String givenAnswer,
                                String correctAnswer,
                                boolean correct,
                                String explanation,
                                String sourceReference) {
}
