package com.examprep.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.examprep.ai.dto.GeneratedStudyPackage;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.api.model.Difficulty;
import com.examprep.api.model.QuestionType;

/**
 * Converts raw model output into safe study material: blank content is dropped, options and answers
 * are structurally validated, and every citation is resolved against the real source chunks.
 */
@Component
public class StudyPackageSanitizer {

    private static final int MAX_TOPICS = 20;
    private static final int MAX_SECTIONS = 24;
    private static final int MAX_DEFINITIONS = 50;
    private static final int MAX_FORMULAS = 25;
    private static final int MAX_FLASHCARDS = 60;
    private static final int MAX_QUESTIONS = 60;
    /** flashcards.source_ref and questions.source_ref are VARCHAR(200) in the database. */
    private static final int MAX_SOURCE_LABEL = 200;

    public GeneratedStudyPackage sanitize(GeneratedStudyPackage raw, SourceReferences references,
                                          StudyPreferences preferences, String fallbackTitle) {
        if (raw == null) {
            throw new AiResponseException("The AI service returned no study material. Please generate the package again.");
        }
        String title = clean(raw.title(), 300);
        String introduction = clean(raw.introduction(), 4000);
        String summary = clean(raw.summary(), 12000);
        List<String> objectives = cleanList(raw.learningObjectives(), 12, 400);
        List<String> areas = cleanList(raw.keyAreasToRevise(), 12, 400);

        List<GeneratedStudyPackage.Topic> topics = new ArrayList<>();
        if (raw.topics() != null) {
            for (GeneratedStudyPackage.Topic topic : raw.topics()) {
                if (topics.size() >= MAX_TOPICS) {
                    break;
                }
                if (topic == null) {
                    continue;
                }
                String name = clean(topic.name(), 200);
                String explanation = clean(topic.explanation(), 5000);
                if (name == null || explanation == null) {
                    continue;
                }
                topics.add(new GeneratedStudyPackage.Topic(name, explanation,
                        cleanList(topic.keyConcepts(), 12, 300),
                        references.resolveAll(nullSafe(topic.sources()))));
            }
        }

        List<GeneratedStudyPackage.RevisionSection> sections = new ArrayList<>();
        if (raw.revisionSections() != null) {
            for (GeneratedStudyPackage.RevisionSection section : raw.revisionSections()) {
                if (sections.size() >= MAX_SECTIONS) {
                    break;
                }
                if (section == null) {
                    continue;
                }
                String heading = clean(section.heading(), 200);
                String body = clean(section.body(), 12000);
                if (heading == null || body == null) {
                    continue;
                }
                sections.add(new GeneratedStudyPackage.RevisionSection(heading, body,
                        references.resolveAll(nullSafe(section.sources()))));
            }
        }

        List<GeneratedStudyPackage.Definition> definitions = new ArrayList<>();
        Set<String> seenTerms = new HashSet<>();
        if (raw.definitions() != null) {
            for (GeneratedStudyPackage.Definition definition : raw.definitions()) {
                if (definitions.size() >= MAX_DEFINITIONS) {
                    break;
                }
                if (definition == null) {
                    continue;
                }
                String term = clean(definition.term(), 200);
                String text = clean(definition.definition(), 3000);
                if (term == null || text == null || !seenTerms.add(term.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String source = references.resolveToLabel(definition.source());
                definitions.add(new GeneratedStudyPackage.Definition(term, text, source));
            }
        }

        List<GeneratedStudyPackage.Formula> formulas = new ArrayList<>();
        if (raw.formulas() != null) {
            for (GeneratedStudyPackage.Formula formula : raw.formulas()) {
                if (formulas.size() >= MAX_FORMULAS) {
                    break;
                }
                if (formula == null) {
                    continue;
                }
                String name = clean(formula.name(), 200);
                String expression = clean(formula.expression(), 600);
                if (name == null || expression == null) {
                    continue;
                }
                formulas.add(new GeneratedStudyPackage.Formula(name, expression,
                        clean(formula.explanation(), 2000), references.resolveToLabel(formula.source())));
            }
        }

        List<GeneratedStudyPackage.Flashcard> flashcards = new ArrayList<>();
        Set<String> seenFronts = new HashSet<>();
        if (raw.flashcards() != null) {
            for (GeneratedStudyPackage.Flashcard flashcard : raw.flashcards()) {
                if (flashcards.size() >= MAX_FLASHCARDS) {
                    break;
                }
                if (flashcard == null) {
                    continue;
                }
                String front = clean(flashcard.front(), 600);
                String back = clean(flashcard.back(), 3000);
                if (front == null || back == null || !seenFronts.add(front.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                flashcards.add(new GeneratedStudyPackage.Flashcard(front, back,
                        sourceLabel(references.resolveToLabel(flashcard.source()))));
            }
        }

        List<GeneratedStudyPackage.Question> questions = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        if (raw.questions() != null) {
            for (GeneratedStudyPackage.Question question : raw.questions()) {
                if (questions.size() >= MAX_QUESTIONS) {
                    break;
                }
                GeneratedStudyPackage.Question valid = validateQuestion(question, references);
                if (valid != null && seenQuestions.add(valid.question().toLowerCase(Locale.ROOT))) {
                    questions.add(valid);
                }
            }
        }

        if (topics.isEmpty() && sections.isEmpty() && flashcards.isEmpty() && questions.isEmpty()) {
            throw new AiResponseException("The AI response did not contain usable study material. "
                    + "Please generate the package again.");
        }
        if (questions.isEmpty()) {
            // Practice and mock examination need questions; this is not a usable study package.
            throw new AiResponseException("The AI service did not return any valid practice questions. "
                    + "Please generate the package again.");
        }
        // Keep as few as one valid question: startExam() already refuses to begin a mock examination
        // without four auto-scored questions, so rejecting a package here only hid usable practice
        // material behind a generation failure.
        return new GeneratedStudyPackage(
                title == null ? fallbackTitle : title,
                introduction == null ? "" : introduction,
                summary == null ? "" : summary,
                objectives,
                areas,
                topics,
                sections,
                definitions,
                formulas,
                flashcards,
                questions);
    }

    private GeneratedStudyPackage.Question validateQuestion(GeneratedStudyPackage.Question raw,
                                                            SourceReferences references) {
        if (raw == null) {
            return null;
        }
        String text = clean(raw.question(), 2000);
        if (text == null) {
            return null;
        }
        QuestionType type = parseType(raw.type());
        if (type == null) {
            return null;
        }
        // Keep the model's option order but drop blanks and duplicates, then remap the correct index.
        // De-duplication shifts every later position, and reusing the raw index silently stored the wrong
        // answer ("No change" instead of "Decrease" for [Increase, Increase, Decrease, No change]).
        List<String> rawOptions = nullSafe(raw.options());
        List<String> options = new ArrayList<>();
        Map<String, Integer> retainedIndexByOption = new HashMap<>();
        for (int index = 0; index < rawOptions.size() && options.size() < 6; index++) {
            String option = clean(rawOptions.get(index), 500);
            if (option == null) {
                continue;
            }
            String key = option.toLowerCase(Locale.ROOT);
            if (retainedIndexByOption.containsKey(key)) {
                continue;
            }
            retainedIndexByOption.put(key, options.size());
            options.add(option);
        }
        Integer correctIndex = remapOptionIndex(raw.correctOptionIndex(), rawOptions, retainedIndexByOption);
        Boolean correctBoolean = raw.correctAnswer();
        String suggestedAnswer = clean(raw.suggestedAnswer(), 3000);
        switch (type) {
            case MULTIPLE_CHOICE -> {
                if (options.size() < 2) {
                    return null;
                }
                if (correctIndex == null || correctIndex < 0 || correctIndex >= options.size()) {
                    return null;
                }
            }
            case TRUE_FALSE -> {
                options = List.of("True", "False");
                if (correctBoolean == null && correctIndex != null) {
                    correctBoolean = correctIndex == 0;
                }
                if (correctBoolean == null) {
                    return null;
                }
                correctIndex = correctBoolean ? 0 : 1;
            }
            case SHORT_ANSWER -> {
                options = List.of();
                if (suggestedAnswer == null) {
                    return null;
                }
                correctIndex = null;
                correctBoolean = null;
            }
            default -> {
                return null;
            }
        }
        Difficulty difficulty = parseDifficulty(raw.difficulty());
        String explanation = clean(raw.explanation(), 3000);
        String source = sourceLabel(references.resolveToLabel(raw.source()));
        return new GeneratedStudyPackage.Question(type.name(), text, options, correctIndex, correctBoolean,
                suggestedAnswer, explanation == null ? "" : explanation, difficulty.name(), source);
    }

    /** Maps the model's original option index onto the de-duplicated option list. */
    private static Integer remapOptionIndex(Integer rawIndex, List<String> rawOptions,
                                            Map<String, Integer> retainedIndexByOption) {
        if (rawIndex == null || rawIndex < 0 || rawIndex >= rawOptions.size()) {
            return null;
        }
        String chosen = clean(rawOptions.get(rawIndex), 500);
        return chosen == null ? null : retainedIndexByOption.get(chosen.toLowerCase(Locale.ROOT));
    }

    /** Citation labels list every resolved location, which can exceed the stored column width. */
    private static String sourceLabel(String label) {
        if (label == null) {
            return null;
        }
        return label.length() <= MAX_SOURCE_LABEL ? label : label.substring(0, MAX_SOURCE_LABEL).strip();
    }

    static QuestionType parseType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "_");
        if (normalized.contains("MULTIPLE") || normalized.contains("MCQ")) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (normalized.contains("TRUE") || normalized.contains("FALSE") || normalized.equals("TF")) {
            return QuestionType.TRUE_FALSE;
        }
        if (normalized.contains("SHORT")) {
            return QuestionType.SHORT_ANSWER;
        }
        return null;
    }

    static Difficulty parseDifficulty(String value) {
        if (value == null) {
            return Difficulty.MEDIUM;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EASY", "BEGINNER", "LOW" -> Difficulty.EASY;
            case "HARD", "ADVANCED", "HIGH" -> Difficulty.HARD;
            default -> Difficulty.MEDIUM;
        };
    }

    private List<String> cleanList(List<String> values, int maxItems, int maxLength) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return cleaned;
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (cleaned.size() >= maxItems) {
                break;
            }
            String item = clean(value, maxLength);
            if (item != null && seen.add(item.toLowerCase(Locale.ROOT))) {
                cleaned.add(item);
            }
        }
        return cleaned;
    }

    private List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("```json", "").replace("```", "").strip();
        if (cleaned.isBlank()) {
            return null;
        }
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength).strip();
    }

    /** Exposed for diagnostics: reference IDs that are valid for the package. */
    public Map<String, String> debugReferences(SourceReferences references) {
        return Map.of("known", references.describeKnown());
    }
}
