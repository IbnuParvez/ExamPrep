package com.examprep.ai.exception;

/**
 * Raised when the AI provider rate limits the application.
 */
public class AiRateLimitException extends AiUnavailableException {

    public AiRateLimitException(String message) {
        super(message);
    }
}
