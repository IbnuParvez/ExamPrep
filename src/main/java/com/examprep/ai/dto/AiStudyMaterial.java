package com.examprep.ai.dto;

import java.util.List;

/**
 * Raw shape requested from the model for a complete study package. Kept separate from the domain
 * so untrusted model output is validated before it is used.
 */
public record AiStudyMaterial(String title,
                              String introduction,
                              String summary,
                              List<String> learningObjectives,
                              List<String> revisionPriorities,
                              List<AiTopic> topics,
                              List<AiRevisionSection> revisionSections,
                              List<AiDefinition> definitions,
                              List<AiFormula> formulas,
                              List<String> keyFacts,
                              List<AiFlashcard> flashcards,
                              List<AiQuestion> questions,
                              Integer examDurationMinutes) {
}
