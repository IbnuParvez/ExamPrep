package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

/**
 * A question as shown to the student. Correct answers are deliberately absent so that the
 * browser can never inspect them before submission.
 */
public record QuestionDto(UUID id,
                          QuestionType type,
                          String prompt,
                          List<String> options,
                          Difficulty difficulty,
                          String sourceReference) {
}
