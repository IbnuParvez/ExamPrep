package com.examprep.ai.client;

import com.examprep.ai.config.GeminiProperties;
import com.examprep.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies controlled retries with exponential backoff for transient provider failures. Content
 * failures (invalid JSON) are handled by the JSON client, not retried here.
 */
@Service
public class GoogleGeminiGateway implements GeminiGateway {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeminiGateway.class);
    private static final long BASE_BACKOFF_MILLIS = 700;

    private final GeminiTransport transport;
    private final GeminiProperties properties;

    public GoogleGeminiGateway(GeminiTransport transport, GeminiProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String modelName() {
        return properties.resolvedModel();
    }

    @Override
    public String generateJson(GeminiPrompt prompt) {
        properties.requireApiKey();
        int attempts = Math.max(0, properties.maxRetries()) + 1;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return transport.generateContent(new GeminiTransport.GeminiCall(
                        properties.resolvedModel(),
                        prompt.systemInstruction(),
                        prompt.userPrompt(),
                        prompt.maxOutputTokens(),
                        properties.timeoutSeconds()));
            } catch (AiUnavailableException transientFailure) {
                // AiRateLimitException is a subtype; both are transient provider failures.
                lastFailure = transientFailure;
                if (attempt < attempts) {
                    long delay = BASE_BACKOFF_MILLIS * (1L << (attempt - 1));
                    log.info("AI request failed on attempt {} of {}; retrying in {} ms",
                            attempt, attempts, delay);
                    sleep(delay);
                }
            }
        }
        throw lastFailure;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("The AI request was interrupted. Please try again.");
        }
    }
}
