package com.examprep.ai.service;

import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.FormulaDto;
import com.examprep.api.study.RevisionSectionDto;
import com.examprep.api.study.TopicDto;
import java.util.List;

/**
 * Validated, citation-checked study material ready to be persisted by the study module.
 */
public record StudyMaterialDraft(String title,
                                 String introduction,
                                 String summary,
                                 List<String> learningObjectives,
                                 List<String> revisionPriorities,
                                 List<TopicDto> topics,
                                 List<RevisionSectionDto> revisionNotes,
                                 List<DefinitionDto> definitions,
                                 List<FormulaDto> formulas,
                                 List<String> keyFacts,
                                 List<FlashcardDraft> flashcards,
                                 List<QuestionDraft> questions,
                                 int examDurationMinutes) {
}
