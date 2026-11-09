package com.examprep.ai.client;

/** Isolates the rest of the application from Google Gen AI SDK details. */
public interface GeminiGateway {

    /**
     * Sends one prompt to Gemini and returns the raw text response.
     *
     * @throws com.examprep.ai.exception.AiUnavailableException when no API key is configured
     * @throws com.examprep.ai.exception.AiServiceException     when the provider fails after retries
     */
    String generate(GeminiPrompt prompt);
}
