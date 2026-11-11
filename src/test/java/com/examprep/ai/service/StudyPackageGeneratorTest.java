package com.examprep.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.examprep.api.dto.DocumentChunk;

/** Source excerpts must cover the whole document, not only its opening pages. */
class StudyPackageGeneratorTest {

    @Test
    void keepsEveryChunkWhenTheDocumentFitsTheBudget() {
        List<DocumentChunk> chunks = chunks(4, 100);

        assertThat(StudyPackageGenerator.selectChunksWithinBudget(chunks, 400)).hasSize(4);
    }

    @Test
    void samplesAcrossTheWholeDocumentWhenItExceedsTheBudget() {
        List<DocumentChunk> chunks = chunks(40, 100);

        List<DocumentChunk> selected = StudyPackageGenerator.selectChunksWithinBudget(chunks, 1_000);

        int characters = selected.stream().mapToInt(chunk -> chunk.text().length()).sum();
        assertThat(characters).isLessThanOrEqualTo(1_000);
        assertThat(selected).isNotEmpty();
        // Chunks from the back half of the document must be represented.
        assertThat(selected).anyMatch(chunk -> chunk.ordinal() > 20);
        assertThat(selected.get(0).ordinal()).isEqualTo(1);
    }

    private List<DocumentChunk> chunks(int count, int size) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            chunks.add(new DocumentChunk("c" + index, index, "Section " + index, "x".repeat(size)));
        }
        return chunks;
    }
}
