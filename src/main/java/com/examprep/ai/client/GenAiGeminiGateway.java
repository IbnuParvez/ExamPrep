package com.examprep.ai.client;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.examprep.ai.config.GeminiClientFactory;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.config.AiProperties;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

/**
 * Official Google Gen AI Java SDK implementation. Prompts and completions are never logged: they
 * may contain the student's study material.
 */
@Component
public class GenAiGeminiGateway implements GeminiGateway {

    private static final Logger log = LoggerFactory.getLogger(GenAiGeminiGateway.class);

    private final GeminiClientFactory clientFactory;
    private final AiProperties properties;

    public GenAiGeminiGateway(GeminiClientFactory clientFactory, AiProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    @Override
    public String generate(GeminiPrompt prompt) {
        Client client = clientFactory.client();
        Content systemInstruction = Content.fromParts(Part.fromText(prompt.systemInstruction()));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .responseMimeType(prompt.json() ? "application/json" : "text/plain")
                .temperature((float) properties.getTemperature())
                .maxOutputTokens(prompt.maxOutputTokens() > 0
                        ? prompt.maxOutputTokens()
                        : properties.getMaxOutputTokens())
                .build();

        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                GenerateContentResponse response = client.models.generateContent(
                        properties.getModel(), prompt.userPrompt(), config);
                String text = response == null ? null : response.text();
                if (text == null || text.isBlank()) {
                    throw new AiServiceException("Gemini returned an empty response. Please try again.");
                }
                return text.strip();
            } catch (AiServiceException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                boolean transientFailure = isTransient(exception);
                log.warn("Gemini request failed on attempt {}/{} with {} (transient={})",
                        attempt, attempts, exception.getClass().getSimpleName(), transientFailure);
                if (!transientFailure || attempt == attempts) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new AiServiceException(friendlyMessage(lastFailure), lastFailure);
    }

    private void sleepBeforeRetry(int attempt) {
        long base = 900L * (1L << Math.min(attempt - 1, 4));
        long jitter = ThreadLocalRandom.current().nextLong(0, 400);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
        }
    }

    private boolean isTransient(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            String name = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            if (name.contains("servererror") || name.contains("timeout") || name.contains("ioexception")) {
                return true;
            }
            if (message.contains("429") || message.contains("rate limit") || message.contains("resource_exhausted")
                    || message.contains("500") || message.contains("502") || message.contains("503")
                    || message.contains("504") || message.contains("unavailable")
                    || message.contains("deadline") || message.contains("timed out")
                    || message.contains("timeout") || message.contains("connection reset")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String friendlyMessage(RuntimeException failure) {
        String message = collectMessages(failure).toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("rate limit") || message.contains("resource_exhausted")
                || message.contains("quota")) {
            return "Gemini is rate limiting requests right now. Wait a moment, then try again.";
        }
        if (message.contains("deadline") || message.contains("timed out") || message.contains("timeout")) {
            return "Gemini did not respond in time. Try again, or upload a shorter document.";
        }
        if (message.contains("api key") || message.contains("permission") || message.contains("401")
                || message.contains("403")) {
            return "Gemini rejected the configured API key. Check GOOGLE_API_KEY and that the Gemini API is enabled.";
        }
        if (message.contains("not found") || message.contains("404")) {
            return "The configured Gemini model is not available to this API key. Check GEMINI_MODEL.";
        }
        return "Gemini could not be reached or returned an error. Please try again shortly.";
    }

    private String collectMessages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 5) {
            builder.append(current.getMessage()).append(' ');
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }
}
