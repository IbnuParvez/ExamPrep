package com.examprep.common;

/**
 * Raised when user input is syntactically valid but cannot be accepted by the application.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
