package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * General purpose fallback extraction for legacy Word, legacy PowerPoint, RTF and
 * OpenDocument files. These formats do not expose reliable page boundaries through Tika, so
 * the whole document is reported as a single general source rather than inventing locations.
 */
@Component
@Order(100)
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentTextExtractor.class);
    private static final int MAX_CHARACTERS = 4_000_000;

    @Override
    public boolean supports(DocumentFormat format) {
        return switch (format) {
            case DOC, PPT, RTF, ODT, ODP -> true;
            default -> false;
        };
    }

    @Override
    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARACTERS);
        Metadata metadata = new Metadata();
        try (InputStream input = Files.newInputStream(file)) {
            parser.parse(input, handler, metadata, new ParseContext());
            String text = TextNormaliser.normalise(handler.toString());
            if (text.isBlank()) {
                return new ExtractedDocument(null, List.of());
            }
            return new ExtractedDocument(null, List.of(new ExtractedSection("Document", null, text)));
        } catch (Exception failure) {
            log.warn("Tika extraction failed: {}", failure.getClass().getSimpleName());
            throw new DocumentExtractionException(
                    "The document could not be read. The file may be corrupted or damaged.", failure);
        }
    }
}
