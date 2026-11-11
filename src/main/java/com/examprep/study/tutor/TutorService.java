package com.examprep.study.tutor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.examprep.ai.client.AiGateway;
import com.examprep.ai.client.AiPrompt;
import com.examprep.ai.dto.TutorModelResponse;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.prompt.Prompts;
import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.dto.DocumentChunk;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.TutorAnswerDto;
import com.examprep.config.AiProperties;
import com.examprep.study.domain.TutorMessageEntity;
import com.examprep.study.domain.TutorRole;
import com.examprep.study.repository.TutorMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Answers questions from the student's own document. Relevant chunks are retrieved lexically and
 * passed as context, and the model is instructed to admit when the material is insufficient.
 */
@Service
public class TutorService {

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);
    private static final int HISTORY_MESSAGES = 10;
    /** A tutor answer is a short explanation; 4096 tokens is plenty and keeps latency predictable. */
    private static final int TUTOR_MAX_OUTPUT_TOKENS = 4_096;

    private final DocumentApplicationService documentService;
    private final ChunkRetriever chunkRetriever;
    private final AiGateway gateway;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final TutorMessageRepository messageRepository;

    public TutorService(DocumentApplicationService documentService, ChunkRetriever chunkRetriever,
                        AiGateway gateway, ObjectMapper objectMapper, AiProperties properties,
                        TutorMessageRepository messageRepository) {
        this.documentService = documentService;
        this.chunkRetriever = chunkRetriever;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.messageRepository = messageRepository;
    }

    public TutorAnswerDto answer(UUID ownerId, UUID studyPackId, UUID documentId, String question) {
        DocumentContext context = documentService.getContext(documentId, ownerId);
        List<DocumentChunk> retrieved = chunkRetriever.retrieve(
                context.chunks().toArray(new DocumentChunk[0]), question,
                properties.getTutorChunkLimit(), properties.getTutorContextChars());
        if (retrieved.isEmpty()) {
            throw new AiResponseException("This document has no extracted text to search.");
        }
        SourceReferences references = SourceReferences.from(retrieved);
        String conversation = renderHistory(studyPackId);

        String prompt = Prompts.tutorPrompt(context.title(), Prompts.renderSources(retrieved), conversation, question);
        Duration timeout = Duration.ofSeconds(Math.max(10, properties.getTutorTimeoutSeconds()));
        String raw = gateway.generate(AiPrompt.json(Prompts.TUTOR_SYSTEM_INSTRUCTION, prompt,
                TUTOR_MAX_OUTPUT_TOKENS, timeout));
        TutorModelResponse response;
        try {
            response = parse(raw);
        } catch (AiResponseException unreadable) {
            // One repair attempt: the model can usually reformat its own answer, and without this the
            // student saw a failure for a purely syntactic problem.
            log.warn("Tutor response could not be parsed; requesting one repair attempt");
            response = parse(gateway.generate(AiPrompt.json(Prompts.TUTOR_SYSTEM_INSTRUCTION,
                    Prompts.tutorJsonRepairPrompt(raw), TUTOR_MAX_OUTPUT_TOKENS, timeout)));
        }
        String answer = response.answer() == null ? "" : response.answer().strip();
        if (answer.isBlank()) {
            throw new AiResponseException("The tutor did not return an answer. Please try again.");
        }
        boolean grounded = Boolean.TRUE.equals(response.answeredFromMaterial());
        List<String> sources = grounded && response.sources() != null
                ? references.resolveAll(response.sources())
                : List.of();
        return new TutorAnswerDto(answer, sources, grounded, java.time.Instant.now());
    }

    private String renderHistory(UUID studyPackId) {
        List<TutorMessageEntity> history = messageRepository.findByStudyPackIdOrderByCreatedAtAsc(studyPackId);
        int fromIndex = Math.max(0, history.size() - HISTORY_MESSAGES);
        return history.subList(fromIndex, history.size()).stream()
                .map(message -> (message.getRole() == TutorRole.USER ? "Student: " : "Tutor: ")
                        + message.getContent())
                .collect(Collectors.joining("\n"));
    }

    private TutorModelResponse parse(String raw) {
        String candidate = raw == null ? "" : raw.strip();
        if (candidate.startsWith("```")) {
            int firstNewline = candidate.indexOf('\n');
            int lastFence = candidate.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                candidate = candidate.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiResponseException("The tutor returned an unreadable response. Please try again.");
        }
        try {
            return objectMapper.readValue(candidate.substring(start, end + 1), TutorModelResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn("Tutor response could not be parsed");
            throw new AiResponseException("The tutor returned an unreadable response. Please try again.", exception);
        }
    }
}
