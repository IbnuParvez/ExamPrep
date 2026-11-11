package com.examprep.ai.exception;

import com.examprep.common.AppException;

/** The AI service returned something that could not be validated into safe study material. */
public class AiResponseException extends AppException {

    public AiResponseException(String message) {
        super(message);
    }

    public AiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
