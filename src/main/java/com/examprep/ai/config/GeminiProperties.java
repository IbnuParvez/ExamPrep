package com.examprep.ai.config;

import com.examprep.ai.exception.AiConfigurationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini integration settings. The API key is read from the environment and is never
 * written to logs, templates or HTTP responses.
 */
@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiProperties(String apiKey,
                               String model,
                               int timeoutSeconds,
                               int maxRetries,
                               int maxOutputTokens) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String requireApiKey() {
        if (!isConfigured()) {
            throw new AiConfigurationException(
                    "The Gemini API key is not configured. Set the GOOGLE_API_KEY environment variable and restart the application.");
        }
        return apiKey.trim();
    }

    public String resolvedModel() {
        return model == null || model.isBlank() ? "gemini-3.8-flash" : model.trim();
    }

    /**
     * Redacted representation so configuration dumps can never leak the secret.
     */
    @Override
    public String toString() {
        return "GeminiProperties[model=" + resolvedModel() + ", configured=" + isConfigured()
                + ", timeoutSeconds=" + timeoutSeconds + ", maxRetries=" + maxRetries + "]";
    }
}
