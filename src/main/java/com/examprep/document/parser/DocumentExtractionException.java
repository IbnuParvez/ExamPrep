package com.examprep.document.parser;

/**
 * Raised when a document cannot be parsed. The message is safe to show to the student.
 */
public class DocumentExtractionException extends Exception {

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
