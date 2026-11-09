package com.examprep.document.parser;

import java.util.List;

/** Extracted document content. Page count is only set when it is genuinely known. */
public record ExtractionResult(List<ExtractedBlock> blocks, Integer pageCount) {
}
