package com.examprep.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.examprep.ai.client.AiGateway;
import com.examprep.ai.client.AiPrompt;
import com.examprep.ai.dto.GeneratedStudyPackage;
import com.examprep.ai.dto.SegmentAnalysis;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.prompt.Prompts;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.dto.DocumentChunk;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.StudyPreferences;
import com.examprep.config.AiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds a study package from real extracted chunks. Large documents are first analysed in
 * segments; those analyses then guide the single structured generation request.
 */
@Service
public class StudyPackageGenerator {

    private static final Logger log = LoggerFactory.getLogger(StudyPackageGenerator.class);

    /** How much of an unusable previous answer is shown to the model when asking for a repair. */
    private static final int REPAIR_PREVIEW_CHARS = 8_000;
    /** Segment analyses are short; a smaller ceiling leaves more of the budget for the final answer. */
    private static final int SEGMENT_MAX_OUTPUT_TOKENS = 4_096;

    private final AiGateway gateway;
    private final StudyPackageSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public StudyPackageGenerator(AiGateway gateway, StudyPackageSanitizer sanitizer,
                                 ObjectMapper objectMapper, AiProperties properties) {
        this.gateway = gateway;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public GeneratedStudyPackage generate(DocumentContext context, StudyPreferences preferences,
                                          ProgressReporter reporter) {
        List<DocumentChunk> chunks = context.chunks();
        if (chunks.isEmpty()) {
            throw new AiResponseException("The document does not contain any extracted text to study.");
        }
        SourceReferences references = SourceReferences.from(chunks);
        // Excerpts are sampled across the whole document, not just its first pages: the segment analysis
        // covers every chunk, and the system prompt forbids using facts outside the supplied excerpts.
        List<DocumentChunk> promptChunks = selectChunksWithinBudget(chunks, properties.getMaxSourceChars());

        String analysis = "";
        if (context.totalChars() > properties.getMaxSourceChars()) {
            reporter.report("Analysing this long document in sections", 20);
            analysis = analyseInSegments(context, reporter);
        }

        reporter.report("Drafting summaries, revision notes and questions", 65);
        String prompt = Prompts.studyPackagePrompt(context.title(), preferences,
                Prompts.renderSources(promptChunks), analysis);
        GeneratedStudyPackage sanitized = generatePackage(prompt, references, preferences, context.title());
        log.info("Generated study package with {} topics, {} flashcards and {} questions",
                sanitized.topics().size(), sanitized.flashcards().size(), sanitized.questions().size());
        return sanitized;
    }

    /**
     * Requests the study package and repairs it exactly once if the result cannot be used. A repair is
     * also triggered when the JSON parses but the sanitizer rejects it, which used to fail the whole
     * job even though the model could have fixed its own output.
     */
    private GeneratedStudyPackage generatePackage(String prompt, SourceReferences references,
                                                  StudyPreferences preferences, String fallbackTitle) {
        String raw = gateway.generate(AiPrompt.json(Prompts.SYSTEM_INSTRUCTION, prompt,
                properties.getMaxOutputTokens()));
        try {
            return sanitizer.sanitize(parse(raw, GeneratedStudyPackage.class), references, preferences, fallbackTitle);
        } catch (AiResponseException unusable) {
            log.warn("The AI returned study material that could not be used; requesting one repair");
            String repaired = gateway.generate(AiPrompt.json(Prompts.SYSTEM_INSTRUCTION,
                    repairPrompt(raw, unusable.getMessage()), properties.getMaxOutputTokens()));
            return sanitizer.sanitize(parse(repaired, GeneratedStudyPackage.class), references, preferences,
                    fallbackTitle);
        }
    }

    private String analyseInSegments(DocumentContext context, ProgressReporter reporter) {
        List<List<DocumentChunk>> segments = splitIntoSegments(context.chunks(), properties.getSegmentCharLimit());
        StringBuilder combined = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            List<DocumentChunk> segment = segments.get(index);
            reporter.report("Analysing section " + (index + 1) + " of " + segments.size(),
                    20 + (int) (35.0 * index / Math.max(1, segments.size())));
            String prompt = Prompts.segmentAnalysisPrompt(context.title(), Prompts.renderSources(segment));
            SegmentAnalysis analysis = generateJson(Prompts.SYSTEM_INSTRUCTION, prompt, SegmentAnalysis.class,
                    SEGMENT_MAX_OUTPUT_TOKENS);
            combined.append("- Section ").append(index + 1).append(": ")
                    .append(analysis.summary() == null ? "" : analysis.summary().strip()).append('\n');
            if (analysis.keyPoints() != null) {
                for (String keyPoint : analysis.keyPoints()) {
                    if (keyPoint != null && !keyPoint.isBlank()) {
                        combined.append("  * ").append(keyPoint.strip()).append('\n');
                    }
                }
            }
            if (analysis.topics() != null && !analysis.topics().isEmpty()) {
                combined.append("  Topics: ").append(String.join(", ", analysis.topics())).append('\n');
            }
        }
        return combined.toString().strip();
    }

    static List<List<DocumentChunk>> splitIntoSegments(List<DocumentChunk> chunks, int charLimit) {
        List<List<DocumentChunk>> segments = new ArrayList<>();
        List<DocumentChunk> current = new ArrayList<>();
        int size = 0;
        for (DocumentChunk chunk : chunks) {
            int chunkSize = chunk.text().length();
            if (!current.isEmpty() && size + chunkSize > charLimit) {
                segments.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(chunk);
            size += chunkSize;
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    /**
     * Selects excerpts evenly across the entire document within the character budget, keeping them in
     * ascending order. Taking only the leading chunks used to starve the back half of any document
     * longer than the budget, so questions came only from the first few pages.
     */
    public static List<DocumentChunk> selectChunksWithinBudget(List<DocumentChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }
        int budget = Math.max(1, maxChars);
        long total = 0;
        for (DocumentChunk chunk : chunks) {
            total += chunk.text().length();
        }
        if (total <= budget) {
            return new ArrayList<>(chunks);
        }
        double averageChunk = Math.max(1.0, (double) total / chunks.size());
        int targetCount = Math.max(1, Math.min(chunks.size(), (int) Math.floor(budget / averageChunk)));

        Set<Integer> indices = new LinkedHashSet<>();
        if (targetCount == 1) {
            indices.add(0);
        } else {
            for (int position = 0; position < targetCount; position++) {
                indices.add((int) Math.round((double) position * (chunks.size() - 1) / (targetCount - 1)));
            }
        }

        List<DocumentChunk> selected = new ArrayList<>();
        int used = 0;
        for (int index : indices) {
            DocumentChunk chunk = chunks.get(index);
            int size = chunk.text().length();
            if (used + size > budget) {
                continue;
            }
            selected.add(chunk);
            used += size;
        }
        if (selected.isEmpty()) {
            selected.add(chunks.get(0));
        }
        return selected;
    }

    /** Requests JSON and repairs a malformed response exactly once. Raw completions are never logged. */
    private <T> T generateJson(String systemInstruction, String prompt, Class<T> type, int maxOutputTokens) {
        String raw = gateway.generate(AiPrompt.json(systemInstruction, prompt, maxOutputTokens));
        try {
            return parse(raw, type);
        } catch (AiResponseException firstFailure) {
            log.warn("The AI returned malformed JSON; requesting one repair attempt");
            String repaired = gateway.generate(AiPrompt.json(systemInstruction,
                    repairPrompt(raw, "the response was not a valid JSON object"), maxOutputTokens));
            return parse(repaired, type);
        }
    }

    /**
     * Asks the model to fix its own output. The original (potentially huge) source prompt is deliberately
     * not resent: the previous answer already contains the material, and resending the sources doubled
     * token usage for the same output-token ceiling.
     */
    private static String repairPrompt(String previousOutput, String reason) {
        return """
                Your previous output could not be used: %s

                Return ONLY a corrected JSON object for the same request, with no markdown fences and no
                commentary. Reuse the material already present in your previous output and do not add facts
                that were not in it.

                For a study package the object needs these keys: title, introduction, summary,
                learningObjectives, keyAreasToRevise, topics[], revisionSections[], definitions[], formulas[],
                flashcards[], questions[]. Every question needs type (MULTIPLE_CHOICE, TRUE_FALSE or
                SHORT_ANSWER), question, options (empty for TRUE_FALSE and SHORT_ANSWER), correctOptionIndex
                (multiple choice, 0-based into options), correctAnswer (true/false only), suggestedAnswer
                (short answer only), explanation, difficulty and source. Include at least four
                MULTIPLE_CHOICE or TRUE_FALSE questions and never repeat the same option text.

                For a section analysis the object needs: summary, keyPoints[], topics[].

                Previous output:
                %s
                """.formatted(reason, truncate(previousOutput, REPAIR_PREVIEW_CHARS));
    }

    private <T> T parse(String raw, Class<T> type) {
        String candidate = extractJson(raw);
        try {
            T value = objectMapper.readValue(candidate, type);
            if (value == null) {
                throw new AiResponseException("The AI service returned an empty JSON document.");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new AiResponseException("The AI service returned a response that could not be validated. "
                    + "Please try generating the study package again.", exception);
        }
    }

    static String extractJson(String raw) {
        if (raw == null) {
            throw new AiResponseException("The AI service returned no content.");
        }
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiResponseException("The AI service did not return a JSON object.");
        }
        return cleaned.substring(start, end + 1);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
