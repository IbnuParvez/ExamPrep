package com.examprep.ai.exception;

/**
 * Raised when generated content cannot be validated even after a repair attempt.
 */
public class AiGenerationException extends RuntimeException {

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
