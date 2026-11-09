package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.nio.file.Path;

/**
 * Extracts structured text from a single supported format family.
 */
public interface DocumentTextExtractor {

    boolean supports(DocumentFormat format);

    ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException;
}
