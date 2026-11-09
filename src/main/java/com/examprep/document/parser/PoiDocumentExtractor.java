package com.examprep.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.examprep.common.ProcessingFailedException;

/**
 * Structural extraction for Microsoft Office formats. Using POI directly preserves real slide,
 * heading and table boundaries instead of inventing page numbers after a flat text dump.
 */
@Component
public class PoiDocumentExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("docx", "doc", "pptx", "ppt");
    private static final String UNTITLED_SECTION = "Opening section";

    @Override
    public boolean supports(String extension) {
        return SUPPORTED.contains(extension.toLowerCase());
    }

    @Override
    public ExtractionResult extract(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".docx")) {
                return extractDocx(file);
            }
            if (name.endsWith(".pptx")) {
                return extractPptx(file);
            }
            if (name.endsWith(".doc")) {
                return extractDoc(file);
            }
            if (name.endsWith(".ppt")) {
                return extractPpt(file);
            }
        } catch (IOException | RuntimeException exception) {
            throw new ProcessingFailedException("This Office document could not be read. It may be corrupted "
                    + "or password protected.", exception);
        }
        throw new ProcessingFailedException("Unsupported Office document format.");
    }

    private ExtractionResult extractDocx(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<ExtractedBlock> blocks = new ArrayList<>();
            String currentHeading = UNTITLED_SECTION;
            StringBuilder section = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
                    if (text.isBlank()) {
                        continue;
                    }
                    if (isHeading(paragraph)) {
                        appendSection(blocks, currentHeading, section);
                        currentHeading = "Section: " + shorten(text);
                        section.setLength(0);
                        section.append(text).append('\n');
                    } else {
                        section.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    section.append(renderTable(table)).append('\n');
                }
            }
            appendSection(blocks, currentHeading, section);
            return new ExtractionResult(blocks, null);
        }
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.startsWith("heading") || lower.contains("title")) {
                return true;
            }
        }
        return paragraph.getRuns().stream().anyMatch(run -> run.isBold() && run.getFontSize() >= 14)
                && paragraph.getText() != null
                && paragraph.getText().length() < 120;
    }

    private String renderTable(XWPFTable table) {
        StringBuilder builder = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().replace('\n', ' ').strip());
            }
            builder.append(String.join(" | ", cells)).append('\n');
        }
        return builder.toString().strip();
    }

    private ExtractionResult extractPptx(Path file) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(Files.newInputStream(file))) {
            List<ExtractedBlock> blocks = new ArrayList<>();
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value.strip()).append('\n');
                        }
                    }
                }
                if (!text.isEmpty()) {
                    blocks.add(new ExtractedBlock("Slide " + index, text.toString().strip()));
                }
                index++;
            }
            return new ExtractionResult(blocks, slideShow.getSlides().size());
        }
    }

    private ExtractionResult extractPpt(Path file) throws IOException {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(Files.newInputStream(file))) {
            List<ExtractedBlock> blocks = new ArrayList<>();
            int index = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value.strip()).append('\n');
                        }
                    }
                }
                if (!text.isEmpty()) {
                    blocks.add(new ExtractedBlock("Slide " + index, text.toString().strip()));
                }
                index++;
            }
            return new ExtractionResult(blocks, slideShow.getSlides().size());
        }
    }

    private ExtractionResult extractDoc(Path file) throws IOException {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(file));
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            String text = String.join("\n", paragraphs).strip();
            List<ExtractedBlock> blocks = new ArrayList<>();
            if (!text.isBlank()) {
                blocks.add(new ExtractedBlock("Document text", text));
            }
            return new ExtractionResult(blocks, null);
        }
    }

    private void appendSection(List<ExtractedBlock> blocks, String label, StringBuilder section) {
        String text = section.toString().strip();
        if (!text.isBlank()) {
            blocks.add(new ExtractedBlock(label, text));
        }
    }

    private String shorten(String value) {
        String trimmed = value.strip();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    /** Kept to make the supported slide-show generic types explicit for readers. */
    @SuppressWarnings("unused")
    private void unusedTypeHints(SlideShow<?, ?> slides, TextShape<?, ?> text) {
    }
}
