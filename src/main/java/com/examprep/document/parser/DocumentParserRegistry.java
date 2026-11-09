package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chooses the most precise extractor available for a format.
 */
@Component
public class DocumentParserRegistry {

    private final List<DocumentTextExtractor> extractors;

    public DocumentParserRegistry(List<DocumentTextExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        return extractors.stream()
                .filter(extractor -> extractor.supports(format))
                .findFirst()
                .orElseThrow(() -> new DocumentExtractionException(
                        "Files of type ." + format.extension() + " are not supported."))
                .extract(file, format);
    }
}
