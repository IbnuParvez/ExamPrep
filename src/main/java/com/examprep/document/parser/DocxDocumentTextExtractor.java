package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * DOCX extraction that keeps heading boundaries, which lets generated material cite the
 * section a statement came from instead of inventing page numbers.
 */
@Component
@Order(30)
public class DocxDocumentTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxDocumentTextExtractor.class);

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.DOCX;
    }

    @Override
    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        try (InputStream input = Files.newInputStream(file);
             XWPFDocument word = new XWPFDocument(input)) {
            List<ExtractedSection> sections = new ArrayList<>();
            StringBuilder buffer = new StringBuilder();
            String heading = null;

            for (IBodyElement element : word.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = TextNormaliser.normalise(paragraph.getText());
                    if (text.isBlank()) {
                        continue;
                    }
                    if (isHeading(paragraph)) {
                        flush(sections, heading, buffer);
                        heading = text.length() <= 300 ? text : text.substring(0, 300);
                    } else {
                        buffer.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    buffer.append(renderTable(table)).append('\n');
                }
            }
            flush(sections, heading, buffer);

            if (sections.isEmpty()) {
                String fallback = TextNormaliser.normalise(word.getDocument().getBody().xmlText());
                if (!fallback.isBlank()) {
                    sections.add(new ExtractedSection("Document", null, fallback));
                }
            }
            return new ExtractedDocument(null, sections);
        } catch (IOException | RuntimeException failure) {
            log.warn("DOCX extraction failed: {}", failure.getClass().getSimpleName());
            throw new DocumentExtractionException(
                    "The Word document could not be read. The file may be corrupted or damaged.", failure);
        }
    }

    private static boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null || style.isBlank()) {
            return false;
        }
        String normalized = style.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.startsWith("heading") || normalized.startsWith("title")
                || normalized.matches("[1-9]");
    }

    private static String renderTable(XWPFTable table) {
        StringBuilder builder = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = TextNormaliser.normalise(cell.getText());
                if (!cellText.isBlank()) {
                    cells.add(cellText.replace('\n', ' '));
                }
            }
            if (!cells.isEmpty()) {
                builder.append(String.join(" | ", cells)).append('\n');
            }
        }
        return builder.toString();
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
