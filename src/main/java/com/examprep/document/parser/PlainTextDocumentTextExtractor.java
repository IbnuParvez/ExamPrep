package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Plain text and Markdown extraction. Markdown headings become sections, which preserves the
 * author's own structure.
 */
@Component
@Order(40)
public class PlainTextDocumentTextExtractor implements DocumentTextExtractor {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.*$");

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.TXT || format == DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        String raw = read(file);
        String text = TextNormaliser.normalise(raw);
        if (text.isBlank()) {
            return new ExtractedDocument(null, List.of());
        }
        if (format != DocumentFormat.MARKDOWN) {
            return new ExtractedDocument(null, List.of(new ExtractedSection("Document", null, text)));
        }

        List<ExtractedSection> sections = new ArrayList<>();
        String heading = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (MARKDOWN_HEADING.matcher(line.trim()).matches()) {
                flush(sections, heading, buffer);
                heading = line.trim().replaceFirst("^#+\\s*", "");
            } else {
                buffer.append(line).append('\n');
            }
        }
        flush(sections, heading, buffer);
        if (sections.isEmpty()) {
            sections.add(new ExtractedSection("Document", null, text));
        }
        return new ExtractedDocument(null, sections);
    }

    private String read(Path file) throws DocumentExtractionException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException notUtf8) {
            try {
                return Files.readString(file, StandardCharsets.ISO_8859_1);
            } catch (IOException failure) {
                throw new DocumentExtractionException("The text file could not be read.", failure);
            }
        } catch (IOException failure) {
            throw new DocumentExtractionException("The text file could not be read.", failure);
        }
    }

    private static void flush(List<ExtractedSection> sections, String heading, StringBuilder buffer) {
        String text = TextNormaliser.normalise(buffer.toString());
        buffer.setLength(0);
        if (!text.isBlank()) {
            String location = heading == null ? "Document" : "Section: " + heading;
            sections.add(new ExtractedSection(location, heading, text));
        }
    }
}
