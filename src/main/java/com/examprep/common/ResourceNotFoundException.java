package com.examprep.common;

/**
 * Thrown when a requested resource does not exist or is not owned by the current student.
 * The two cases are intentionally indistinguishable so that identifiers cannot be probed.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " " + id + " was not found.");
    }
}
