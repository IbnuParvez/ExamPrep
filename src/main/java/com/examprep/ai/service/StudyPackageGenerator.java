package com.examprep.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
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

    private final GeminiGateway gateway;
    private final StudyPackageSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public StudyPackageGenerator(GeminiGateway gateway, StudyPackageSanitizer sanitizer,
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
        List<DocumentChunk> promptChunks = takeWithinBudget(chunks, properties.getMaxSourceChars());

        String analysis = "";
        if (context.totalChars() > properties.getMaxSourceChars()) {
            reporter.report("Analysing this long document in sections", 20);
            analysis = analyseInSegments(context, reporter);
        }

        reporter.report("Drafting summaries, revision notes and questions", 65);
        String prompt = Prompts.studyPackagePrompt(context.title(), preferences,
                Prompts.renderSources(promptChunks), analysis);
        String raw = generateJsonWithRepair(Prompts.SYSTEM_INSTRUCTION, prompt);
        GeneratedStudyPackage parsed = parse(raw, GeneratedStudyPackage.class);
        GeneratedStudyPackage sanitized = sanitizer.sanitize(parsed, references, preferences, context.title());
        log.info("Generated study package with {} topics, {} flashcards and {} questions",
                sanitized.topics().size(), sanitized.flashcards().size(), sanitized.questions().size());
        return sanitized;
    }

    private String analyseInSegments(DocumentContext context, ProgressReporter reporter) {
        List<List<DocumentChunk>> segments = splitIntoSegments(context.chunks(), properties.getSegmentCharLimit());
        StringBuilder combined = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            List<DocumentChunk> segment = segments.get(index);
            reporter.report("Analysing section " + (index + 1) + " of " + segments.size(),
                    20 + (int) (35.0 * index / Math.max(1, segments.size())));
            String prompt = Prompts.segmentAnalysisPrompt(context.title(), Prompts.renderSources(segment));
            String raw = generateJsonWithRepair(Prompts.SYSTEM_INSTRUCTION, prompt);
            SegmentAnalysis analysis = parse(raw, SegmentAnalysis.class);
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

    private List<DocumentChunk> takeWithinBudget(List<DocumentChunk> chunks, int maxChars) {
        List<DocumentChunk> selected = new ArrayList<>();
        int used = 0;
        for (DocumentChunk chunk : chunks) {
            if (!selected.isEmpty() && used + chunk.text().length() > maxChars) {
                break;
            }
            selected.add(chunk);
            used += chunk.text().length();
        }
        return selected;
    }

    /**
     * Requests JSON and repairs a malformed response exactly once. Raw prompts and completions are
     * never logged.
     */
    private String generateJsonWithRepair(String systemInstruction, String prompt) {
        String raw = gateway.generate(GeminiPrompt.json(systemInstruction, prompt, properties.getMaxOutputTokens()));
        try {
            parse(raw, com.fasterxml.jackson.databind.JsonNode.class);
            return raw;
        } catch (AiResponseException firstFailure) {
            log.warn("Gemini returned malformed JSON; requesting one repair attempt");
            String repair = """
                    Your previous output could not be parsed as the required JSON object.
                    Return ONLY a corrected JSON object for the original task, with no markdown fences or commentary.

                    Previous output:
                    %s

                    Original task:
                    %s
                    """.formatted(truncate(raw, 8000), prompt);
            return gateway.generate(GeminiPrompt.json(systemInstruction, repair, properties.getMaxOutputTokens()));
        }
    }

    private <T> T parse(String raw, Class<T> type) {
        String candidate = extractJson(raw);
        try {
            T value = objectMapper.readValue(candidate, type);
            if (value == null) {
                throw new AiResponseException("Gemini returned an empty JSON document.");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new AiResponseException("Gemini returned a response that could not be validated. "
                    + "Please try generating the study package again.", exception);
        }
    }

    static String extractJson(String raw) {
        if (raw == null) {
            throw new AiResponseException("Gemini returned no content.");
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
            throw new AiResponseException("Gemini did not return a JSON object.");
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
