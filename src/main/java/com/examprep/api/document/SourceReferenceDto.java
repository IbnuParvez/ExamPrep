package com.examprep.api.document;

/**
 * A validated pointer back to a chunk of the original document.
 */
public record SourceReferenceDto(String referenceId, String locationLabel) {
}
