package com.examprep.common;

/**
 * Raised when stored document bytes cannot be written, read or removed.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
