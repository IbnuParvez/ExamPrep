package com.examprep.ai.service;

import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.dto.AiDefinition;
import com.examprep.ai.dto.AiFlashcard;
import com.examprep.ai.dto.AiFormula;
import com.examprep.ai.dto.AiOutline;
import com.examprep.ai.dto.AiQuestion;
import com.examprep.ai.dto.AiResources;
import com.examprep.ai.dto.AiRevisionSection;
import com.examprep.ai.dto.AiSectionSummary;
import com.examprep.ai.dto.AiStudyMaterial;
import com.examprep.ai.dto.AiTopic;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.prompt.PromptLibrary;
import com.examprep.ai.retrieval.SourceReferenceResolver;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.SourceReferenceDto;
import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.FormulaDto;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.RevisionSectionDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.TopicDto;
import com.examprep.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds a study package from extracted document chunks. Small documents are processed in a
 * single request; larger documents are analysed section by section and combined, so no single
 * request contains an entire large document.
 */
@Service
public class StudyMaterialGenerator {

    private static final Logger log = LoggerFactory.getLogger(StudyMaterialGenerator.class);
    private static final int MAX_TOPICS = 10;
    private static final int MAX_REVISION_SECTIONS = 20;
    private static final int MAX_DEFINITIONS = 120;
    private static final int MAX_FORMULAS = 80;
    private static final int MAX_KEY_FACTS = 40;
    private static final int MAX_OBJECTIVES = 12;

    private final GeminiJsonClient jsonClient;
    private final SourceReferenceResolver referenceResolver;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public StudyMaterialGenerator(GeminiJsonClient jsonClient,
                                  SourceReferenceResolver referenceResolver,
                                  AppProperties properties,
                                  ObjectMapper objectMapper) {
        this.jsonClient = jsonClient;
        this.referenceResolver = referenceResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public StudyMaterialDraft generate(DocumentContext context, StudyPreferences preferences,
                                       Consumer<String> progress) {
        AppProperties.Study settings = properties.study();
        int questionCount = clamp(preferences.questionCount() == null
                ? settings.defaultQuestionCount() : preferences.questionCount(),
                1, settings.maxQuestionCount());
        int flashcardCount = clamp(preferences.flashcardCount() == null
                ? settings.defaultFlashcardCount() : preferences.flashcardCount(),
                1, settings.maxFlashcardCount());

        int sourceCharacters = context.chunks().stream()
                .mapToInt(chunk -> chunk.text() == null ? 0 : chunk.text().length())
                .sum();

        AiStudyMaterial material;
        if (sourceCharacters <= settings.maxSourceCharactersPerRequest()) {
            progress.accept("Generating the study package from your document...");
            material = jsonClient.request(
                    new GeminiPrompt(PromptLibrary.SYSTEM_INSTRUCTION,
                            PromptLibrary.fullStudyMaterial(context, preferences, flashcardCount, questionCount),
                            0),
                    AiStudyMaterial.class,
                    this::validateMaterial);
        } else {
            material = generateHierarchically(context, preferences, questionCount, flashcardCount, progress);
        }

        return mapToDraft(material, context, preferences, flashcardCount, questionCount);
    }

    private AiStudyMaterial generateHierarchically(DocumentContext context, StudyPreferences preferences,
                                                   int questionCount, int flashcardCount,
                                                   Consumer<String> progress) {
        List<List<DocumentChunkDto>> batches = batch(context.chunks(),
                Math.max(2000, properties.study().sectionBatchCharacters()));

        List<AiSectionSummary> summaries = new ArrayList<>(batches.size());
        int index = 0;
        for (List<DocumentChunkDto> batch : batches) {
            index++;
            progress.accept("Analysing section " + index + " of " + batches.size() + "...");
            AiSectionSummary summary = jsonClient.request(
                    new GeminiPrompt(PromptLibrary.SYSTEM_INSTRUCTION,
                            PromptLibrary.sectionSummary(context, batch), 0),
                    AiSectionSummary.class,
                    this::validateSectionSummary);
            summaries.add(summary);
        }

        progress.accept("Organising topics and learning objectives...");
        String summariesText = renderSummaries(summaries);
        AiOutline outline = jsonClient.request(
                new GeminiPrompt(PromptLibrary.SYSTEM_INSTRUCTION,
                        PromptLibrary.outline(context, summariesText, preferences), 0),
                AiOutline.class,
                this::validateOutline);

        progress.accept("Writing revision notes, flashcards and questions...");
        String outlineJson;
        try {
            outlineJson = objectMapper.writeValueAsString(outline);
        } catch (JsonProcessingException failure) {
            throw new AiGenerationException("The generated outline could not be processed.", failure);
        }
        AiResources resources = jsonClient.request(
                new GeminiPrompt(PromptLibrary.SYSTEM_INSTRUCTION,
                        PromptLibrary.resources(context, summariesText, outlineJson, preferences,
                                flashcardCount, questionCount), 0),
                AiResources.class,
                this::validateResources);

        return new AiStudyMaterial(
                outline.title(),
                outline.introduction(),
                outline.summary(),
                outline.learningObjectives(),
                outline.revisionPriorities(),
                outline.topics(),
                resources.revisionSections(),
                mergeDefinitions(resources.definitions(), summaries),
                mergeFormulas(resources.formulas(), summaries),
                resources.keyFacts(),
                resources.flashcards(),
                resources.questions(),
                resources.examDurationMinutes());
    }

    private StudyMaterialDraft mapToDraft(AiStudyMaterial material, DocumentContext context,
                                          StudyPreferences preferences, int flashcardCount,
                                          int questionCount) {
        List<DocumentChunkDto> chunks = context.chunks();

        String title = firstNonBlank(material.title(), stripExtension(context.originalFilename()),
                "Study package");
        String summary = requireText(material.summary(), "the overall summary");
        String introduction = firstNonBlank(material.introduction(), summary);
        if (summary.length() > 20000) {
            summary = summary.substring(0, 20000);
        }

        List<TopicDto> topics = new ArrayList<>();
        if (material.topics() != null) {
            for (AiTopic topic : material.topics()) {
                if (topics.size() >= MAX_TOPICS) {
                    break;
                }
                if (isBlank(topic.title()) || isBlank(topic.explanation())) {
                    continue;
                }
                topics.add(new TopicDto(topic.title().trim(), topic.explanation().trim(),
                        cleanList(topic.keyConcepts(), 12),
                        referenceResolver.resolve(topic.sourceRefs(), chunks)));
            }
        }
        if (topics.isEmpty()) {
            throw new AiGenerationException("the study package did not contain any topics");
        }

        List<RevisionSectionDto> notes = new ArrayList<>();
        if (material.revisionSections() != null) {
            for (AiRevisionSection section : material.revisionSections()) {
                if (notes.size() >= MAX_REVISION_SECTIONS) {
                    break;
                }
                if (isBlank(section.heading()) || isBlank(section.narrative())) {
                    continue;
                }
                notes.add(new RevisionSectionDto(section.heading().trim(), section.narrative().trim(),
                        cleanList(section.keyPoints(), 20),
                        referenceResolver.resolve(section.sourceRefs(), chunks)));
            }
        }
        if (notes.isEmpty()) {
            throw new AiGenerationException("the study package did not contain revision notes");
        }

        List<DefinitionDto> definitions = new ArrayList<>();
        if (material.definitions() != null) {
            for (AiDefinition definition : material.definitions()) {
                if (definitions.size() >= MAX_DEFINITIONS) {
                    break;
                }
                if (isBlank(definition.term()) || isBlank(definition.definition())) {
                    continue;
                }
                definitions.add(new DefinitionDto(definition.term().trim(), definition.definition().trim(),
                        referenceResolver.resolveLocation(definition.sourceRef(), chunks)));
            }
        }

        List<FormulaDto> formulas = new ArrayList<>();
        if (material.formulas() != null) {
            for (AiFormula formula : material.formulas()) {
                if (formulas.size() >= MAX_FORMULAS) {
                    break;
                }
                if (isBlank(formula.name()) || isBlank(formula.expression())) {
                    continue;
                }
                formulas.add(new FormulaDto(formula.name().trim(), formula.expression().trim(),
                        isBlank(formula.explanation()) ? "" : formula.explanation().trim(),
                        referenceResolver.resolveLocation(formula.sourceRef(), chunks)));
            }
        }

        List<FlashcardDraft> flashcards = new ArrayList<>();
        if (material.flashcards() != null) {
            for (AiFlashcard flashcard : material.flashcards()) {
                if (flashcards.size() >= flashcardCount) {
                    break;
                }
                if (isBlank(flashcard.front()) || isBlank(flashcard.back())) {
                    continue;
                }
                flashcards.add(new FlashcardDraft(trimTo(flashcard.front(), 1000), trimTo(flashcard.back(), 2000),
                        referenceResolver.resolveLocation(flashcard.sourceRef(), chunks)));
            }
        }
        if (flashcards.isEmpty()) {
            throw new AiGenerationException("the study package did not contain any flashcards");
        }

        List<QuestionDraft> questions = new ArrayList<>();
        if (material.questions() != null) {
            for (AiQuestion question : material.questions()) {
                if (questions.size() >= questionCount) {
                    break;
                }
                QuestionDraft draft = mapQuestion(question, chunks);
                if (draft != null) {
                    questions.add(draft);
                }
            }
        }
        if (questions.isEmpty()) {
            throw new AiGenerationException("the study package did not contain any practice questions");
        }

        int duration = material.examDurationMinutes() == null
                ? properties.study().defaultExamDurationMinutes()
                : material.examDurationMinutes();
        if (preferences.examDurationMinutes() != null) {
            duration = preferences.examDurationMinutes();
        }
        duration = clamp(duration, 5, 180);

        return new StudyMaterialDraft(title, introduction, summary,
                cleanList(material.learningObjectives(), MAX_OBJECTIVES),
                cleanList(material.revisionPriorities(), MAX_OBJECTIVES),
                topics, notes, definitions, formulas,
                cleanList(material.keyFacts(), MAX_KEY_FACTS),
                flashcards, questions, duration);
    }

    private QuestionDraft mapQuestion(AiQuestion question, List<DocumentChunkDto> chunks) {
        if (isBlank(question.prompt()) || isBlank(question.correctAnswer()) || isBlank(question.explanation())) {
            return null;
        }
        QuestionType type = parseType(question.type());
        if (type == null) {
            return null;
        }
        List<String> options;
        String correctAnswer = question.correctAnswer().trim();

        if (type == QuestionType.MULTIPLE_CHOICE) {
            options = cleanList(question.options(), 6);
            if (options.size() < 2) {
                return null;
            }
            String expected = correctAnswer;
            String matched = options.stream()
                    .filter(option -> option.equalsIgnoreCase(expected))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                return null;
            }
            correctAnswer = matched;
        } else if (type == QuestionType.TRUE_FALSE) {
            options = List.of("True", "False");
            String normalized = correctAnswer.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("t")) {
                correctAnswer = "True";
            } else if (normalized.startsWith("f")) {
                correctAnswer = "False";
            } else {
                return null;
            }
        } else {
            options = List.of();
            if (correctAnswer.length() > 2000) {
                return null;
            }
        }

        return new QuestionDraft(type, trimTo(question.prompt(), 2000), options, correctAnswer,
                trimTo(question.explanation(), 2000), Difficulty.fromModelValue(question.difficulty()),
                referenceResolver.resolveLocation(question.sourceRef(), chunks),
                type != QuestionType.SHORT_ANSWER);
    }

    private void validateMaterial(AiStudyMaterial material) {
        if (material == null) {
            throw new AiGenerationException("the response was empty");
        }
        if (isBlank(material.summary())) {
            throw new AiGenerationException("the study package was missing its summary");
        }
        if (material.topics() == null || material.topics().isEmpty()) {
            throw new AiGenerationException("the study package was missing topics");
        }
        if (material.revisionSections() == null || material.revisionSections().isEmpty()) {
            throw new AiGenerationException("the study package was missing revision notes");
        }
        if (material.flashcards() == null || material.flashcards().isEmpty()) {
            throw new AiGenerationException("the study package was missing flashcards");
        }
        if (material.questions() == null || material.questions().isEmpty()) {
            throw new AiGenerationException("the study package was missing practice questions");
        }
    }

    private void validateSectionSummary(AiSectionSummary summary) {
        if (summary == null || isBlank(summary.summary())) {
            throw new AiGenerationException("a section summary was empty");
        }
    }

    private void validateOutline(AiOutline outline) {
        if (outline == null || outline.topics() == null || outline.topics().isEmpty()) {
            throw new AiGenerationException("the outline did not contain topics");
        }
        if (isBlank(outline.summary())) {
            throw new AiGenerationException("the outline did not contain an overview");
        }
    }

    private void validateResources(AiResources resources) {
        if (resources == null || resources.revisionSections() == null || resources.revisionSections().isEmpty()) {
            throw new AiGenerationException("the revision notes were empty");
        }
        if (resources.flashcards() == null || resources.flashcards().isEmpty()) {
            throw new AiGenerationException("no flashcards were produced");
        }
        if (resources.questions() == null || resources.questions().isEmpty()) {
            throw new AiGenerationException("no practice questions were produced");
        }
    }

    private static String renderSummaries(List<AiSectionSummary> summaries) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (AiSectionSummary summary : summaries) {
            index++;
            builder.append("SECTION ").append(index).append(": ")
                    .append(summary.heading() == null ? "Untitled" : summary.heading()).append('\n');
            builder.append(summary.summary()).append('\n');
            if (summary.keyPoints() != null && !summary.keyPoints().isEmpty()) {
                builder.append("Key points:\n");
                for (String point : summary.keyPoints()) {
                    if (!isBlank(point)) {
                        builder.append("- ").append(point.trim()).append('\n');
                    }
                }
            }
            if (summary.sourceRefs() != null && !summary.sourceRefs().isEmpty()) {
                builder.append("Source references: ").append(String.join(", ", summary.sourceRefs()))
                        .append('\n');
            }
            builder.append('\n');
        }
        String text = builder.toString();
        int limit = 60000;
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static List<AiDefinition> mergeDefinitions(List<AiDefinition> primary, List<AiSectionSummary> summaries) {
        List<AiDefinition> merged = new ArrayList<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        for (AiSectionSummary summary : summaries) {
            if (summary.definitions() != null) {
                merged.addAll(summary.definitions());
            }
        }
        return merged;
    }

    private static List<AiFormula> mergeFormulas(List<AiFormula> primary, List<AiSectionSummary> summaries) {
        List<AiFormula> merged = new ArrayList<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        for (AiSectionSummary summary : summaries) {
            if (summary.formulas() != null) {
                merged.addAll(summary.formulas());
            }
        }
        return merged;
    }

    private static List<List<DocumentChunkDto>> batch(List<DocumentChunkDto> chunks, int characterLimit) {
        List<List<DocumentChunkDto>> batches = new ArrayList<>();
        List<DocumentChunkDto> current = new ArrayList<>();
        int size = 0;
        for (DocumentChunkDto chunk : chunks) {
            int chunkSize = chunk.text() == null ? 0 : chunk.text().length();
            if (!current.isEmpty() && size + chunkSize > characterLimit) {
                batches.add(List.copyOf(current));
                current.clear();
                size = 0;
            }
            current.add(chunk);
            size += chunkSize;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private static QuestionType parseType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.startsWith("MULTIPLE") || normalized.equals("MCQ")) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (normalized.startsWith("TRUE") || normalized.equals("TF") || normalized.equals("BOOLEAN")) {
            return QuestionType.TRUE_FALSE;
        }
        if (normalized.startsWith("SHORT") || normalized.equals("OPEN")) {
            return QuestionType.SHORT_ANSWER;
        }
        return null;
    }

    private static List<String> cleanList(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            cleaned.add(value.trim());
            if (cleaned.size() >= limit) {
                break;
            }
        }
        return List.copyOf(cleaned);
    }

    private static String requireText(String value, String description) {
        if (isBlank(value)) {
            throw new AiGenerationException("the study package was missing " + description);
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String trimTo(String value, int limit) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
