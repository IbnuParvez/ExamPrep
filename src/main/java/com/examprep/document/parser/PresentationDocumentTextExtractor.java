package com.examprep.document.parser;

import com.examprep.document.domain.DocumentFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Slide aware extraction for PPTX presentations, so AI material can cite "Slide N".
 */
@Component
@Order(20)
public class PresentationDocumentTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PresentationDocumentTextExtractor.class);

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PPTX;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExtractedDocument extract(Path file, DocumentFormat format) throws DocumentExtractionException {
        try (InputStream input = Files.newInputStream(file);
             XMLSlideShow show = new XMLSlideShow(input)) {
            List<? extends Slide<?, ?>> slides = show.getSlides();
            List<ExtractedSection> sections = new ArrayList<>(slides.size());
            int slideNumber = 0;
            for (Slide<?, ?> slide : slides) {
                slideNumber++;
                List<String> parts = new ArrayList<>();
                String heading = null;
                for (Shape<?, ?> shape : slide.getShapes()) {
                    if (shape instanceof TextShape<?, ?> textShape) {
                        String text = TextNormaliser.normalise(textShape.getText());
                        if (text.isBlank()) {
                            continue;
                        }
                        if (heading == null && text.length() <= 160) {
                            heading = text.lines().findFirst().orElse(null);
                        }
                        parts.add(text);
                    }
                }
                String combined = TextNormaliser.normalise(String.join("\n", parts));
                if (!combined.isBlank()) {
                    sections.add(new ExtractedSection("Slide " + slideNumber, heading, combined));
                }
            }
            return new ExtractedDocument(slides.size(), sections);
        } catch (IOException | RuntimeException failure) {
            log.warn("Presentation extraction failed: {}", failure.getClass().getSimpleName());
            throw new DocumentExtractionException(
                    "The presentation could not be read. The file may be corrupted or damaged.", failure);
        }
    }
}
