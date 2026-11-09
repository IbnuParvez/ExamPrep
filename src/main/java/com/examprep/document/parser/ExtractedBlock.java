package com.examprep.document.parser;

/** A structurally meaningful block of extracted text with a truthful location label. */
public record ExtractedBlock(String locationLabel, String text) {
}
