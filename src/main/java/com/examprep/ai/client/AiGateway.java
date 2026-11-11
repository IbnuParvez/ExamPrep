package com.examprep.ai.client;

/** Isolates the rest of the application from the OpenAI-compatible HTTP router details. */
public interface AiGateway {

    /**
     * Sends one prompt to the router and returns the cleaned text response.
     *
     * @throws com.examprep.ai.exception.AiUnavailableException when no key/base URL is configured
     * @throws com.examprep.ai.exception.AiRateLimitException   when the router is out of quota or rate limiting
     * @throws com.examprep.ai.exception.AiServiceException     when the provider fails after retries
     */
    String generate(AiPrompt prompt);
}
