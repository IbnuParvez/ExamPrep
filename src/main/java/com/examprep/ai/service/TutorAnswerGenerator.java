package com.examprep.ai.service;

import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.dto.AiTutorReply;
import com.examprep.ai.exception.AiGenerationException;
import com.examprep.ai.prompt.PromptLibrary;
import com.examprep.ai.retrieval.SourceReferenceResolver;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.SourceReferenceDto;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Produces an answer grounded in retrieved document chunks and only keeps citations that refer
 * to chunks that were actually supplied.
 */
@Service
public class TutorAnswerGenerator {

    private final GeminiJsonClient jsonClient;
    private final SourceReferenceResolver referenceResolver;

    public TutorAnswerGenerator(GeminiJsonClient jsonClient, SourceReferenceResolver referenceResolver) {
        this.jsonClient = jsonClient;
        this.referenceResolver = referenceResolver;
    }

    public TutorAnswer answer(DocumentContext context, List<DocumentChunkDto> retrievedChunks,
                              String studyPackSummary, String conversation, String question) {
        AiTutorReply reply = jsonClient.request(
                new GeminiPrompt(PromptLibrary.SYSTEM_INSTRUCTION,
                        PromptLibrary.tutorAnswer(context, retrievedChunks, studyPackSummary, conversation, question),
                        2048),
                AiTutorReply.class,
                TutorAnswerGenerator::validate);

        List<SourceReferenceDto> references = referenceResolver.resolve(reply.sourceRefs(), retrievedChunks);
        return new TutorAnswer(reply.answer().trim(), references,
                Boolean.TRUE.equals(reply.insufficientInformation()));
    }

    private static void validate(AiTutorReply reply) {
        if (reply == null || reply.answer() == null || reply.answer().isBlank()) {
            throw new AiGenerationException("the tutor response did not contain an answer");
        }
    }

    public record TutorAnswer(String answer, List<SourceReferenceDto> references,
                              boolean insufficientInformation) {
    }
}
