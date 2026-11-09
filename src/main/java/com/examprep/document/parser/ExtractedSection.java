package com.examprep.document.parser;

/**
 * A structural unit found while extracting a document, for example a PDF page, a slide or a
 * headed section in a word processor document.
 *
 * @param locationLabel human readable location, never invented
 * @param heading       heading text when the source exposes one, otherwise {@code null}
 * @param text          extracted text
 */
public record ExtractedSection(String locationLabel, String heading, String text) {
}
