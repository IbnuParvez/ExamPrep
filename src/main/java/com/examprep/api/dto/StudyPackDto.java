package com.examprep.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.examprep.api.model.QuestionType;
import com.examprep.api.model.StudyPackStatus;

/** Complete generated study package, including all learning resources. */
public record StudyPackDto(
        UUID id,
        UUID documentId,
        String documentFilename,
        String title,
        String introduction,
        String summary,
        List<String> learningObjectives,
        List<String> keyAreasToRevise,
        List<Topic> topics,
        List<RevisionSection> revisionSections,
        List<Definition> definitions,
        List<Formula> formulas,
        List<Flashcard> flashcards,
        List<PracticeQuestion> practiceQuestions,
        int examQuestionCount,
        int examDurationMinutes,
        StudyPackStatus status,
        String language,
        String academicLevel,
        Instant createdAt
) {
    public record Topic(String name, String explanation, List<String> keyConcepts, List<String> sources) {
    }

    public record RevisionSection(String heading, String body, List<String> sources) {
    }

    public record Definition(String term, String definition, String source) {
    }

    public record Formula(String name, String expression, String explanation, String source) {
    }

    public record Flashcard(UUID id, int ordinal, String front, String back, String sourceRef) {
    }

    /** Practice question view: deliberately excludes the correct answer and explanation. */
    public record PracticeQuestion(
            UUID id,
            int ordinal,
            QuestionType type,
            String question,
            List<String> options,
            String difficulty,
            String sourceRef
    ) {
    }
}
