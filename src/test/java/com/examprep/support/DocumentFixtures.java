package com.examprep.support;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Builds small but genuine office documents so extraction tests exercise real parsers.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {
    }

    public static byte[] pdf(String... pages) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(50, 700);
                    for (String line : text.split("\n")) {
                        content.showText(line);
                        content.newLineAtOffset(0, -18);
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static byte[] docx(Map<String, String> sections) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            sections.forEach((heading, body) -> {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setStyle("Heading1");
                paragraph.createRun().setText(heading);
                document.createParagraph().createRun().setText(body);
            });
            document.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static byte[] pptx(String... slides) {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : slides) {
                XSLFSlide slide = show.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setAnchor(new Rectangle(40, 40, 600, 160));
                box.setText(text);
            }
            show.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * A minimal but valid JPEG header, used to prove that a renamed file is rejected based on its
     * real content rather than its extension.
     */
    public static byte[] jpegHeader() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F',
                'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00};
    }
}
