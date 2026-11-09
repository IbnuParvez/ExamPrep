package com.examprep.common;

/** A document could not be read, parsed or understood. */
public class ProcessingFailedException extends AppException {

    public ProcessingFailedException(String message) {
        super(message);
    }

    public ProcessingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
