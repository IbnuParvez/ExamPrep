package com.examprep.ai.exception;

import com.examprep.common.AppException;

/** The AI request failed after retries (network, rate limit, provider error). */
public class AiServiceException extends AppException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
