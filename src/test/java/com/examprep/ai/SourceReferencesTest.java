package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.api.dto.DocumentChunk;

class SourceReferencesTest {

    private final SourceReferences references = SourceReferences.from(List.of(
            new DocumentChunk("c3", 3, "Page 4", "text"),
            new DocumentChunk("c4", 4, "Slide 9", "text")));

    @Test
    void resolvesKnownReferencesToRealLocationLabels() {
        assertThat(references.resolveAll(List.of("c3"))).containsExactly("Page 4");
        assertThat(references.resolveAll(List.of("[c3]", "c4"))).containsExactly("Page 4", "Slide 9");
    }

    @Test
    void ignoresInventedReferencesAndPageNumbers() {
        assertThat(references.resolveAll(List.of("c99", "Page 12", "slide 2"))).isEmpty();
        assertThat(references.resolveToLabel("c3, c99")).isEqualTo("Page 4");
        assertThat(references.resolveToLabel("Page 12")).isNull();
    }

    @Test
    void deduplicatesRepeatedLocations() {
        assertThat(references.resolveAll(List.of("c3", "c3", "c4"))).containsExactly("Page 4", "Slide 9");
    }
}
