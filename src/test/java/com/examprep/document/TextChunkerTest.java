package com.examprep.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.examprep.api.dto.DocumentChunk;
import com.examprep.document.parser.ExtractedBlock;
import com.examprep.document.parser.TextChunker;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void keepsPageLabelsAndStableReferences() {
        List<ExtractedBlock> blocks = List.of(
                new ExtractedBlock("Page 1", "Supply and demand describe how prices form in a market. "
                        + "When demand rises and supply is fixed, the equilibrium price increases."),
                new ExtractedBlock("Page 2", "Elasticity measures how responsive quantity is to a price change. "
                        + "Goods with close substitutes tend to be more elastic."));

        List<DocumentChunk> chunks = chunker.chunk(blocks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).refId()).isEqualTo("c1");
        assertThat(chunks.get(0).locationLabel()).isEqualTo("Page 1");
        assertThat(chunks.get(0).ordinal()).isEqualTo(1);
        assertThat(chunks.get(1).refId()).isEqualTo("c2");
        assertThat(chunks.get(1).locationLabel()).isEqualTo("Page 2");
    }

    @Test
    void splitsLongBlocksWithoutCrossingLocationBoundaries() {
        String paragraph = "A sentence that explains a concept clearly. ".repeat(300);
        List<DocumentChunk> chunks = chunker.chunk(List.of(
                new ExtractedBlock("Slide 3", paragraph),
                new ExtractedBlock("Slide 4", "The final slide contains a short but useful summary of the model.")));

        assertThat(chunks.size()).isGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> chunk.text().length() <= TextChunker.MAX_CHARS);
        assertThat(chunks.subList(0, chunks.size() - 1))
                .allMatch(chunk -> chunk.locationLabel().equals("Slide 3"));
        assertThat(chunks.get(chunks.size() - 1).locationLabel()).isEqualTo("Slide 4");
    }

    @Test
    void dropsEmptyBlocksAndWhitespaceOnlyContent() {
        List<DocumentChunk> chunks = chunker.chunk(List.of(
                new ExtractedBlock("Page 1", "     "),
                new ExtractedBlock("Page 2", "Short"),
                new ExtractedBlock("Page 3", "A sufficiently long paragraph of genuine study material.")));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).locationLabel()).isEqualTo("Page 3");
    }

    @Test
    void normalisesInvisibleWhitespace() {
        List<ExtractedBlock> blocks = List.of(new ExtractedBlock("Document text",
                "First line\u00a0with   spaces\r\n\r\n\r\nSecond paragraph with content."));
        List<DocumentChunk> chunks = chunker.chunk(blocks);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).doesNotContain("\u00a0");
        assertThat(chunks.get(0).text()).contains("First line with spaces");
    }
}
