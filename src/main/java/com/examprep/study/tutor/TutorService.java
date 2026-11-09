package com.examprep.study.tutor;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.dto.TutorModelResponse;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.prompt.Prompts;
import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.DocumentApplicationService;
import com.examprep.api.dto.DocumentChunk;
import com.examprep.api.dto.DocumentContext;
import com.examprep.api.dto.StudyPackDto;
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

    private final DocumentApplicationService documentService;
    private final ChunkRetriever chunkRetriever;
    private final GeminiGateway gateway;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final TutorMessageRepository messageRepository;

    public TutorService(DocumentApplicationService documentService, ChunkRetriever chunkRetriever,
                        GeminiGateway gateway, ObjectMapper objectMapper, AiProperties properties,
                        TutorMessageRepository messageRepository) {
        this.documentService = documentService;
        this.chunkRetriever = chunkRetriever;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.messageRepository = messageRepository;
    }

    public TutorAnswerDto answer(java.util.UUID ownerId, StudyPackDto studyPack, String question) {
        DocumentContext context = documentService.getContext(studyPack.documentId(), ownerId);
        List<DocumentChunk> retrieved = chunkRetriever.retrieve(
                context.chunks().toArray(new DocumentChunk[0]), question,
                properties.getTutorChunkLimit(), properties.getTutorContextChars());
        if (retrieved.isEmpty()) {
            throw new AiResponseException("This document has no extracted text to search.");
        }
        SourceReferences references = SourceReferences.from(retrieved);
        String conversation = renderHistory(studyPack.id());

        String prompt = Prompts.tutorPrompt(context.title(), Prompts.renderSources(retrieved), conversation, question);
        String raw = gateway.generate(GeminiPrompt.json(Prompts.TUTOR_SYSTEM_INSTRUCTION, prompt,
                Math.min(properties.getMaxOutputTokens(), 16384)));
        TutorModelResponse response = parse(raw);
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

    private String renderHistory(java.util.UUID studyPackId) {
        List<TutorMessageEntity> history = messageRepository.findByStudyPackIdOrderByCreatedAtAsc(studyPackId);
        int fromIndex = Math.max(0, history.size() - HISTORY_MESSAGES);
        return history.subList(fromIndex, history.size()).stream()
                .map(message -> (message.getRole() == TutorRole.USER ? "Student: " : "Tutor: ")
                        + message.getContent())
                .collect(Collectors.joining("\n"));
    }

    private TutorModelResponse parse(String raw) {
        String candidate = raw.strip();
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
