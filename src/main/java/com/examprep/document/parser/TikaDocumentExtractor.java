package com.examprep.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import com.examprep.common.ProcessingFailedException;

/** General text extraction with Apache Tika for TXT, Markdown, RTF, ODT and ODP. */
@Component
public class TikaDocumentExtractor implements DocumentExtractor {

    private static final int MAX_CHARS = 4_000_000;

    @Override
    public boolean supports(String extension) {
        return true;
    }

    @Override
    public ExtractionResult extract(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            AutoDetectParser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(MAX_CHARS);
            Metadata metadata = new Metadata();
            parser.parse(inputStream, handler, metadata, new ParseContext());
            String text = handler.toString().strip();
            List<ExtractedBlock> blocks = new ArrayList<>();
            if (!text.isBlank()) {
                blocks.add(new ExtractedBlock("Document text", text));
            }
            return new ExtractionResult(blocks, null);
        } catch (org.apache.tika.exception.TikaException exception) {
            throw new ProcessingFailedException("This document could not be parsed. It may be corrupted.", exception);
        } catch (org.xml.sax.SAXException | IOException exception) {
            throw new ProcessingFailedException("This document could not be read.", exception);
        }
    }
}
