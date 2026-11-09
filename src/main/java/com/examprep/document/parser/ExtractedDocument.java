package com.examprep.document.parser;

import java.util.List;

/**
 * Raw extraction result. {@code pageCount} is only set when the source format exposes page
 * or slide boundaries.
 */
public record ExtractedDocument(Integer pageCount, List<ExtractedSection> sections) {

    public ExtractedDocument {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
