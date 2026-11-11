package com.examprep.ai.exception;

import com.examprep.common.AppException;

/** The AI service is not configured on this server, so no AI operation can run. */
public class AiUnavailableException extends AppException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
