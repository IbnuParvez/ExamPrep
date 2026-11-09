package com.examprep.ai.client;

/**
 * Gateway that isolates the rest of the application from the Gemini SDK. All calls request
 * JSON output and return the raw model text for validation by the caller.
 */
public interface GeminiGateway {

    boolean isConfigured();

    String modelName();

    /**
     * @throws com.examprep.ai.exception.AiConfigurationException when no API key is configured
     * @throws com.examprep.ai.exception.AiUnavailableException   when the provider fails or times out
     */
    String generateJson(GeminiPrompt prompt);
}
