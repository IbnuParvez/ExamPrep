package com.examprep.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.examprep.config.AppProperties;
import com.examprep.document.domain.DocumentFormat;
import com.examprep.document.parser.DocumentExtractionException;
import com.examprep.document.parser.DocumentParserRegistry;
import com.examprep.document.parser.ExtractedDocument;
import com.examprep.document.service.ChunkDraft;
import com.examprep.document.service.DocumentChunker;
import com.examprep.support.DocumentFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

class DocumentParserTest {

    private final AppProperties properties = new AppProperties(
            new AppProperties.Storage("target/test-uploads"),
            new AppProperties.Upload(25),
            new AppProperties.Processing(30, 1_500_000, 2400, 1800, 200),
            new AppProperties.Study(24000, 12000, 15, 20, 30, 40, 60),
            new AppProperties.Tutor(6));

    private final DocumentChunker chunker = new DocumentChunker(properties);

    private DocumentParserRegistry registry() {
        List<com.examprep.document.parser.DocumentTextExtractor> extractors = new java.util.ArrayList<>(List.of(
                new com.examprep.document.parser.PdfDocumentTextExtractor(),
                new com.examprep.document.parser.PresentationDocumentTextExtractor(),
                new com.examprep.document.parser.DocxDocumentTextExtractor(),
                new com.examprep.document.parser.PlainTextDocumentTextExtractor(),
                new com.examprep.document.parser.TikaDocumentTextExtractor()));
        extractors.sort(AnnotationAwareOrderComparator.INSTANCE);
        return new DocumentParserRegistry(extractors);
    }

    @Test
    void pdfExtractionKeepsPageReferences(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("lecture.pdf");
        Files.write(pdf, DocumentFixtures.pdf(
                "Photosynthesis occurs in chloroplasts. Light energy becomes chemical energy.",
                "The light reactions produce ATP and NADPH in the thylakoid membrane.",
                "The Calvin cycle fixes carbon dioxide into glucose."));

        ExtractedDocument extracted = registry().extract(pdf, DocumentFormat.PDF);

        assertThat(extracted.pageCount()).isEqualTo(3);
        assertThat(extracted.sections()).extracting(section -> section.locationLabel())
                .containsExactly("Page 1", "Page 2", "Page 3");
        assertThat(extracted.sections().get(1).text()).contains("thylakoid");

        List<ChunkDraft> chunks = chunker.chunk(extracted);
        assertThat(chunks).extracting(ChunkDraft::referenceId)
                .containsExactly("S1", "S2", "S3");
        assertThat(chunks.get(0).locationLabel()).isEqualTo("Page 1");
    }

    @Test
    void docxExtractionKeepsSectionHeadings(@TempDir Path tempDirectory) throws Exception {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("Cell structure", "Cells contain organelles. The nucleus stores genetic material.");
        sections.put("Enzyme activity", "Enzymes are biological catalysts that lower activation energy.");

        Path docx = tempDirectory.resolve("notes.docx");
        Files.write(docx, DocumentFixtures.docx(sections));

        ExtractedDocument extracted = registry().extract(docx, DocumentFormat.DOCX);

        assertThat(extracted.sections()).hasSize(2);
        assertThat(extracted.sections().get(0).heading()).isEqualTo("Cell structure");
        assertThat(extracted.sections().get(0).locationLabel()).isEqualTo("Section: Cell structure");
        assertThat(extracted.sections().get(1).text()).contains("activation energy");
    }

    @Test
    void pptxExtractionKeepsSlideReferences(@TempDir Path tempDirectory) throws Exception {
        Path pptx = tempDirectory.resolve("slides.pptx");
        Files.write(pptx, DocumentFixtures.pptx(
                "Enzymes - learning outcomes",
                "Enzymes lower activation energy",
                "Factors affecting enzyme activity"));

        ExtractedDocument extracted = registry().extract(pptx, DocumentFormat.PPTX);

        assertThat(extracted.pageCount()).isEqualTo(3);
        assertThat(extracted.sections()).extracting(section -> section.locationLabel())
                .containsExactly("Slide 1", "Slide 2", "Slide 3");
    }

    @Test
    void markdownExtractionUsesHeadingSections(@TempDir Path tempDirectory) throws Exception {
        Path markdown = tempDirectory.resolve("revision.md");
        Files.writeString(markdown, """
                # Thermodynamics

                Energy cannot be created or destroyed.

                ## Entropy

                Entropy is a measure of disorder in a system.
                """);

        ExtractedDocument extracted = registry().extract(markdown, DocumentFormat.MARKDOWN);

        assertThat(extracted.sections()).hasSize(2);
        assertThat(extracted.sections().get(0).heading()).isEqualTo("Thermodynamics");
        assertThat(extracted.sections().get(1).locationLabel()).isEqualTo("Section: Entropy");
    }

    @Test
    void rtfExtractionFallsBackToTika(@TempDir Path tempDirectory) throws Exception {
        Path rtf = tempDirectory.resolve("summary.rtf");
        Files.writeString(rtf, "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times;}}\\f0\\fs24 Chemical bonds store energy.}");

        ExtractedDocument extracted = registry().extract(rtf, DocumentFormat.RTF);

        assertThat(extracted.sections()).hasSize(1);
        assertThat(extracted.sections().get(0).text()).contains("Chemical bonds store energy");
        assertThat(extracted.sections().get(0).locationLabel()).isEqualTo("Document");
    }

    @Test
    void corruptedPdfIsRejectedWithAReadableMessage(@TempDir Path tempDirectory) throws IOException {
        Path broken = tempDirectory.resolve("broken.pdf");
        Files.writeString(broken, "%PDF-1.4 this is not really a pdf");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> registry().extract(broken, DocumentFormat.PDF)))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    void longSectionsAreSplitIntoBoundedChunks() {
        String paragraph = "Photosynthesis converts light energy into chemical energy. ".repeat(120);
        ExtractedDocument extracted = new ExtractedDocument(1,
                List.of(new com.examprep.document.parser.ExtractedSection("Page 1", null, paragraph)));

        List<ChunkDraft> chunks = chunker.chunk(extracted);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(2400));
        assertThat(chunks).extracting(ChunkDraft::referenceId).doesNotHaveDuplicates();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.locationLabel()).isEqualTo("Page 1"));
    }
}
