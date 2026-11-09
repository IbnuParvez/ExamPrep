package com.examprep.ai.service;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.config.GeminiProperties;
import com.examprep.ai.exception.AiGenerationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Requests JSON from the model, deserialises it into typed DTOs and validates it. A failed
 * response is retried once with a repair instruction; after that the caller receives a clear
 * generation error instead of partly valid content.
 */
@Service
public class GeminiJsonClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiJsonClient.class);

    private final GeminiGateway gateway;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiJsonClient(GeminiGateway gateway, GeminiProperties properties, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T request(GeminiPrompt prompt, Class<T> type, Consumer<T> validator) {
        GeminiPrompt effectivePrompt = prompt.maxOutputTokens() > 0
                ? prompt
                : new GeminiPrompt(prompt.systemInstruction(), prompt.userPrompt(), properties.maxOutputTokens());

        Attempt<T> first = attempt(effectivePrompt, type, validator);
        if (first.value() != null) {
            return first.value();
        }

        log.info("Retrying an AI request after an invalid response: {}", first.reason());
        GeminiPrompt repair = effectivePrompt.withUserPrompt(effectivePrompt.userPrompt()
                + "\n\nYour previous response was rejected because " + first.reason()
                + ". Return the complete JSON object again, containing only valid JSON and no other text.");
        Attempt<T> second = attempt(repair, type, validator);
        if (second.value() != null) {
            return second.value();
        }
        throw new AiGenerationException("The AI response could not be used: " + second.reason());
    }

    private <T> Attempt<T> attempt(GeminiPrompt prompt, Class<T> type, Consumer<T> validator) {
        String raw;
        try {
            raw = gateway.generateJson(prompt);
        } catch (AiGenerationException generationFailure) {
            throw generationFailure;
        }
        try {
            T parsed = objectMapper.readValue(cleanJson(raw), type);
            if (parsed == null) {
                return Attempt.failure("the response was empty");
            }
            validator.accept(parsed);
            return Attempt.success(parsed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            log.info("AI response was not valid JSON: {}", malformed.getOriginalMessage());
            return Attempt.failure("it was not valid JSON");
        } catch (AiGenerationException invalidContent) {
            return Attempt.failure(invalidContent.getMessage());
        }
    }

    /**
     * Defensive clean-up for models that wrap JSON in markdown fences. The primary strategy is
     * the instruction and response mime type, not string extraction.
     */
    static String cleanJson(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        return cleaned.trim();
    }

    private record Attempt<T>(T value, String reason) {

        static <T> Attempt<T> success(T value) {
            return new Attempt<>(value, null);
        }

        static <T> Attempt<T> failure(String reason) {
            return new Attempt<>(null, reason);
        }
    }
}
