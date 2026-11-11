package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.examprep.ai.retrieval.SourceReferences;
import com.examprep.ai.service.StudyPackageGenerator;
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

    @Test
    void capsLongMultiChunkLabelsAtTheDatabaseColumnWidth() {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder refs = new StringBuilder();
        for (int index = 1; index <= 12; index++) {
            chunks.add(new DocumentChunk("c" + index, index,
                    "Section " + index + " of a long DOCX heading that keeps going for a while", "text"));
            refs.append("c").append(index).append(' ');
        }
        SourceReferences longLabels = SourceReferences.from(chunks);

        String label = longLabels.resolveToLabel(refs.toString());

        assertThat(label).isNotNull();
        assertThat(label.length()).isLessThanOrEqualTo(200);
    }

    @Test
    void samplesChunksAcrossTheWholeDocumentWithinTheBudget() {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int index = 1; index <= 40; index++) {
            chunks.add(new DocumentChunk("c" + index, index, "Section " + index, "x".repeat(100)));
        }

        List<DocumentChunk> selected = StudyPackageGenerator.selectChunksWithinBudget(chunks, 1_000);

        int characters = selected.stream().mapToInt(chunk -> chunk.text().length()).sum();
        assertThat(characters).isLessThanOrEqualTo(1_000);
        assertThat(selected).isNotEmpty();
        assertThat(selected.get(0).ordinal()).isEqualTo(1);
        // The back half of the document must be represented, and order must stay ascending.
        assertThat(selected).anyMatch(chunk -> chunk.ordinal() > 20);
        assertThat(selected.get(selected.size() - 1).ordinal()).isEqualTo(40);
        assertThat(selected).isSortedAccordingTo((first, second) ->
                Integer.compare(first.ordinal(), second.ordinal()));
    }
}
