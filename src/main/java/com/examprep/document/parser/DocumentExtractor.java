package com.examprep.document.parser;

import java.nio.file.Path;

/** Extracts text plus trustworthy page/slide/section boundaries from one document family. */
public interface DocumentExtractor {

    boolean supports(String extension);

    ExtractionResult extract(Path file);
}
