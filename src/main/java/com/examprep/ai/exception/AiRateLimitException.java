package com.examprep.ai.exception;

/** The router is out of quota or rate limiting so hard that retrying now cannot help. */
public class AiRateLimitException extends AiServiceException {

    public AiRateLimitException(String message) {
        super(message);
    }

    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
