package com.examprep.ai.exception;

/**
 * Raised when the AI integration is not usable because of missing or invalid configuration.
 */
public class AiConfigurationException extends RuntimeException {

    public AiConfigurationException(String message) {
        super(message);
    }
}
