package com.examprep.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw structure returned by Gemini before validation. Source fields hold chunk reference IDs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneratedStudyPackage(
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
        List<Question> questions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Topic(String name, String explanation, List<String> keyConcepts, List<String> sources) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RevisionSection(String heading, String body, List<String> sources) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Definition(String term, String definition, String source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Formula(String name, String expression, String explanation, String source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Flashcard(String front, String back, String source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(
            String type,
            String question,
            List<String> options,
            Integer correctOptionIndex,
            Boolean correctAnswer,
            String suggestedAnswer,
            String explanation,
            String difficulty,
            String source
    ) {
    }
}
