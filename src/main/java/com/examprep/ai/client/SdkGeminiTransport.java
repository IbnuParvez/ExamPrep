package com.examprep.ai.client;

import com.examprep.ai.config.GeminiProperties;
import com.examprep.ai.exception.AiRateLimitException;
import com.examprep.ai.exception.AiUnavailableException;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Official Google Gen AI Java SDK transport. The API key is read once from configuration and is
 * never logged or returned.
 */
@Component
public class SdkGeminiTransport implements GeminiTransport {

    private static final Logger log = LoggerFactory.getLogger(SdkGeminiTransport.class);

    private final GeminiProperties properties;
    private volatile Client client;

    public SdkGeminiTransport(GeminiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateContent(GeminiCall call) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(call.systemInstruction())))
                    .responseMimeType("application/json")
                    .temperature(0.35f)
                    .maxOutputTokens(call.maxOutputTokens())
                    .build();

            GenerateContentResponse response = client().models.generateContent(
                    call.model(), call.userPrompt(), config);

            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new AiUnavailableException("The AI model returned an empty response. Please try again.");
            }
            return text;
        } catch (ApiException apiFailure) {
            int status = apiFailure.code();
            if (status == 429) {
                throw new AiRateLimitException("The AI service is rate limiting requests. Please try again shortly.");
            }
            if (status >= 500) {
                throw new AiUnavailableException("The AI service is temporarily unavailable. Please try again shortly.", apiFailure);
            }
            log.warn("Gemini request was rejected with HTTP status {}", status);
            throw new AiUnavailableException(
                    "The AI request was rejected by the provider. Check the configured model and API key.", apiFailure);
        } catch (GenAiIOException ioFailure) {
            throw new AiUnavailableException(
                    "The AI service could not be reached or the request timed out. Please try again.", ioFailure);
        } catch (AiUnavailableException alreadyMapped) {
            throw alreadyMapped;
        } catch (RuntimeException unexpected) {
            log.warn("Gemini call failed: {}", unexpected.getClass().getSimpleName());
            throw new AiUnavailableException("The AI request failed unexpectedly. Please try again.", unexpected);
        }
    }

    private Client client() {
        Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                String apiKey = properties.requireApiKey();
                client = Client.builder()
                        .apiKey(apiKey)
                        .httpOptions(HttpOptions.builder()
                                .timeout(Math.max(10, properties.timeoutSeconds()) * 1000)
                                .build())
                        .build();
            }
            return client;
        }
    }

    @PreDestroy
    void close() {
        Client existing = client;
        if (existing != null) {
            try {
                existing.close();
            } catch (RuntimeException ignored) {
                // shutting down; nothing useful to do
            }
        }
    }
}
