package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Page aware PDF extraction. PDFBox reports one text block per page, which gives reliable
 * "Page N" source references without guessing.
 */
@Component
@Order(10)
public class PdfDocumentTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentTextExtractor.class);
    private static final String PAGE_SEPARATOR = "\u0000EXAMPREP_PAGE\u0000";

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }

    @Override
    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            if (pdf.isEncrypted() && pdf.getNumberOfPages() == 0) {
                throw new DocumentExtractionException(
                        "This PDF is protected and its content could not be read. Remove the password protection and upload it again.");
            }
            int pageCount = pdf.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            // PDFBox 3 exposes page start/end hooks rather than a page separator setting.
            stripper.setPageEnd("\n" + PAGE_SEPARATOR);
            String text = stripper.getText(pdf);

            String[] pages = text.split(java.util.regex.Pattern.quote(PAGE_SEPARATOR), -1);
            List<ExtractedSection> sections = new ArrayList<>(Math.max(pages.length, pageCount));
            for (int index = 0; index < pages.length; index++) {
                String pageText = TextNormaliser.normalise(pages[index]);
                if (!pageText.isBlank()) {
                    sections.add(new ExtractedSection("Page " + (index + 1), null, pageText));
                }
            }
            return new ExtractedDocument(pageCount, sections);
        } catch (InvalidPasswordException passwordProtected) {
            throw new DocumentExtractionException(
                    "This PDF is password protected. Remove the password and upload it again.", passwordProtected);
        } catch (IOException failure) {
            log.warn("PDF extraction failed: {}", failure.getClass().getSimpleName());
            throw new DocumentExtractionException(
                    "The PDF could not be read. The file may be corrupted or damaged.", failure);
        }
    }
}
