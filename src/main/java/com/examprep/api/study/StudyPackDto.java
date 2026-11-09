package com.examprep.api.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete generated study package.
 */
public record StudyPackDto(UUID studyPackId,
                           UUID documentId,
                           String documentFilename,
                           String title,
                           StudyPackStatus status,
                           String language,
                           String academicLevel,
                           String introduction,
                           String summary,
                           List<String> learningObjectives,
                           List<String> revisionPriorities,
                           List<TopicDto> topics,
                           List<RevisionSectionDto> revisionNotes,
                           List<DefinitionDto> definitions,
                           List<FormulaDto> formulas,
                           List<String> keyFacts,
                           List<FlashcardDto> flashcards,
                           List<QuestionDto> practiceQuestions,
                           List<QuestionDto> examQuestions,
                           int examDurationMinutes,
                           String failureReason,
                           Instant createdAt) {
}
