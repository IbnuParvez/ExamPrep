package com.examprep.ai.exception;

/**
 * Raised when the AI provider cannot be reached, times out or repeatedly returns unusable output.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
