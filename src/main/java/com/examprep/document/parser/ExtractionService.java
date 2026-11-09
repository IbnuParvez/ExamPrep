package com.examprep.document.parser;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.examprep.common.ProcessingFailedException;

/** Routes a document to the extractor that understands its structure best. */
@Component
public class ExtractionService {

    private final PdfBoxDocumentExtractor pdfExtractor;
    private final PoiDocumentExtractor poiExtractor;
    private final TikaDocumentExtractor tikaExtractor;

    public ExtractionService(PdfBoxDocumentExtractor pdfExtractor, PoiDocumentExtractor poiExtractor,
                             TikaDocumentExtractor tikaExtractor) {
        this.pdfExtractor = pdfExtractor;
        this.poiExtractor = poiExtractor;
        this.tikaExtractor = tikaExtractor;
    }

    public ExtractionResult extract(Path file, String extension) {
        List<DocumentExtractor> candidates = List.of(pdfExtractor, poiExtractor, tikaExtractor);
        for (DocumentExtractor extractor : candidates) {
            if (extractor.supports(extension)) {
                return extractor.extract(file);
            }
        }
        throw new ProcessingFailedException("This document type cannot be processed.");
    }
}
