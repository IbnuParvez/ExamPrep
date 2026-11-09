package com.examprep.api.document;

/**
 * A stable unit of extracted text used as AI source material.
 *
 * @param referenceId   stable identifier inside the document, for example {@code S7}
 * @param locationLabel human readable source location, for example {@code Page 5}
 * @param heading       nearest heading when one is reliably available, otherwise {@code null}
 * @param text          extracted text
 * @param order         zero based position inside the document
 */
public record DocumentChunkDto(String referenceId,
                               String locationLabel,
                               String heading,
                               String text,
                               int order) {
}
