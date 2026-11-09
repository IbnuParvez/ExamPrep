package com.examprep.ai.retrieval;

import com.examprep.api.document.DocumentChunkDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lightweight lexical retrieval. Documents are split into a few hundred chunks at most, so a
 * term frequency / inverse document frequency score over the chunk set is sufficient and avoids
 * introducing a vector database for the initial release.
 */
@Component
public class DocumentChunkRetriever {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was", "one", "our",
            "out", "day", "get", "has", "him", "his", "how", "its", "may", "new", "now", "old", "see",
            "two", "way", "who", "boy", "did", "she", "use", "with", "this", "that", "from", "what",
            "when", "where", "which", "while", "about", "into", "than", "then", "them", "these", "those",
            "there", "their", "have", "will", "would", "could", "should", "does", "explain", "give",
            "tell", "please", "difference", "between", "example", "based", "notes", "mean", "means");

    public List<DocumentChunkDto> retrieve(List<DocumentChunkDto> chunks, String question, int maxChunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxChunks);
        Set<String> queryTerms = tokenize(question);
        if (queryTerms.isEmpty()) {
            return chunks.subList(0, Math.min(limit, chunks.size()));
        }

        Map<String, Double> idf = inverseDocumentFrequency(chunks);
        List<ScoredChunk> scored = new ArrayList<>(chunks.size());
        for (DocumentChunkDto chunk : chunks) {
            Set<String> chunkTerms = tokenize(chunk.text());
            double score = 0;
            for (String term : queryTerms) {
                if (chunkTerms.contains(term)) {
                    score += idf.getOrDefault(term, 1.0);
                }
            }
            if (chunk.heading() != null) {
                Set<String> headingTerms = tokenize(chunk.heading());
                for (String term : queryTerms) {
                    if (headingTerms.contains(term)) {
                        score += 1.5;
                    }
                }
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score / Math.sqrt(Math.max(1, chunkTerms.size()))));
            }
        }

        if (scored.isEmpty()) {
            return chunks.subList(0, Math.min(limit, chunks.size()));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        List<DocumentChunkDto> result = new ArrayList<>(Math.min(limit, scored.size()));
        for (int index = 0; index < Math.min(limit, scored.size()); index++) {
            result.add(scored.get(index).chunk());
        }
        return List.copyOf(result);
    }

    private static Map<String, Double> inverseDocumentFrequency(List<DocumentChunkDto> chunks) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (DocumentChunkDto chunk : chunks) {
            for (String term : tokenize(chunk.text())) {
                frequencies.merge(term, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>(frequencies.size());
        double total = chunks.size();
        frequencies.forEach((term, count) ->
                idf.put(term, Math.log(1 + (total / (1 + count)))));
        return idf;
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 3 || STOP_WORDS.contains(raw)) {
                continue;
            }
            terms.add(raw);
        }
        return new HashSet<>(terms);
    }

    private record ScoredChunk(DocumentChunkDto chunk, double score) {
    }
}
