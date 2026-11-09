package com.examprep.ai.dto;

import java.util.List;

public record AiResources(List<AiRevisionSection> revisionSections,
                          List<AiDefinition> definitions,
                          List<AiFormula> formulas,
                          List<String> keyFacts,
                          List<AiFlashcard> flashcards,
                          List<AiQuestion> questions,
                          Integer examDurationMinutes) {
}
