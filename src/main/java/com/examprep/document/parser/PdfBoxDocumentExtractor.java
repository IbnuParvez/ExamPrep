package com.examprep.document.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.examprep.common.ProcessingFailedException;

/** Page-aware PDF extraction with Apache PDFBox, so citations can reference real page numbers. */
@Component
public class PdfBoxDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public ExtractionResult extract(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int pageCount = document.getNumberOfPages();
            List<ExtractedBlock> blocks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                if (!text.isBlank()) {
                    blocks.add(new ExtractedBlock("Page " + page, text));
                }
            }
            return new ExtractionResult(blocks, pageCount);
        } catch (InvalidPasswordException exception) {
            throw new ProcessingFailedException("This PDF is password protected, so its text cannot be read. "
                    + "Remove the password and upload it again.", exception);
        } catch (IOException exception) {
            throw new ProcessingFailedException("This PDF could not be opened. It may be corrupted.", exception);
        }
    }

    /** True when the PDF has pages but almost no text, which usually means it is a scan. */
    public boolean looksScanned(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(3, document.getNumberOfPages()));
            return stripper.getText(document).strip().length() < 40;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    static int pageOf(PDPage page) {
        return 0;
    }
}
