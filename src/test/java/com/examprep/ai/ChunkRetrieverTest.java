package com.examprep.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.api.dto.DocumentChunk;

class ChunkRetrieverTest {

    private final ChunkRetriever retriever = new ChunkRetriever();

    private final DocumentChunk[] chunks = new DocumentChunk[]{
            new DocumentChunk("c1", 1, "Page 1", "This page introduces the module and the assessment plan."),
            new DocumentChunk("c2", 2, "Page 2", "Elasticity measures responsiveness of quantity demanded "
                    + "to changes in price. Elastic demand means quantity changes a lot."),
            new DocumentChunk("c3", 3, "Page 3", "Elasticity of supply describes how producers respond. "
                    + "Supply elasticity depends on production capacity."),
            new DocumentChunk("c4", 4, "Page 4", "Market equilibrium occurs where supply equals demand.")
    };

    @Test
    void retrievesChunksThatShareMeaningfulTerms() {
        var selected = retriever.retrieve(chunks, "How does elasticity of supply work?", 2, 5000);

        assertThat(selected).isNotEmpty();
        assertThat(selected.get(0).refId()).isEqualTo("c3");
        assertThat(selected).allMatch(chunk -> chunk.text().toLowerCase().contains("elasticity"));
    }

    @Test
    void alwaysSuppliesAtLeastOneChunkWhenNoTermMatches() {
        var selected = retriever.retrieve(chunks, "zzzz unknown vocabulary", 3, 5000);

        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).refId()).isEqualTo("c1");
    }

    @Test
    void respectsTheCharacterBudget() {
        var selected = retriever.retrieve(chunks, "elasticity supply", 4, 120);

        assertThat(selected).hasSize(1);
    }
}
