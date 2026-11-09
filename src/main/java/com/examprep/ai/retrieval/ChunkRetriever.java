package com.examprep.ai.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.examprep.api.dto.DocumentChunk;

/**
 * Deliberately simple lexical retrieval: no vector database is needed for the initial release.
 * Chunks that share the most meaningful terms with the question are supplied to Gemini as context.
 */
@Component
public class ChunkRetriever {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "about", "what", "which", "when",
            "where", "why", "how", "are", "was", "were", "has", "have", "had", "does", "did", "can", "could",
            "should", "would", "you", "your", "please", "explain", "give", "tell", "using", "based", "between",
            "these", "those", "there", "their", "them", "then", "than", "also", "more", "most", "some", "such",
            "document", "material", "notes", "exam", "question", "question?", "answer", "note"
    );

    public List<DocumentChunk> retrieve(DocumentChunk[] chunks, String query, int limit, int maxChars) {
        List<DocumentChunk> pool = new ArrayList<>(List.of(chunks));
        if (pool.isEmpty()) {
            return List.of();
        }
        Map<DocumentChunk, Double> scores = new HashMap<>();
        List<String> terms = terms(query);
        boolean anyTermMatched = false;
        for (DocumentChunk chunk : pool) {
            String text = chunk.text().toLowerCase(Locale.ROOT);
            double score = 0;
            for (String term : terms) {
                int occurrences = countOccurrences(text, term);
                if (occurrences > 0) {
                    score += Math.min(3, occurrences) * (1.0 + Math.min(term.length(), 10) / 20.0);
                }
            }
            if (score > 0) {
                anyTermMatched = true;
            }
            // Slight preference for the opening material when the query is vague.
            if (chunk.ordinal() <= 3) {
                score += 0.25;
            }
            scores.put(chunk, score);
        }
        if (!anyTermMatched) {
            // Nothing in the question matches the material: supply only the opening chunk so the tutor
            // still has grounding context instead of the whole document.
            return List.of(pool.get(0));
        }

        List<DocumentChunk> ranked = new ArrayList<>(pool);
        ranked.sort(Comparator.comparingDouble((DocumentChunk chunk) -> scores.getOrDefault(chunk, 0.0))
                .reversed()
                .thenComparingInt(DocumentChunk::ordinal));

        List<DocumentChunk> selected = new ArrayList<>();
        int used = 0;
        for (DocumentChunk chunk : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            // Always return at least one chunk so the tutor has grounding material.
            if (!selected.isEmpty() && used + chunk.text().length() > maxChars) {
                continue;
            }
            selected.add(chunk);
            used += chunk.text().length();
        }
        // Most relevant first: the tutor prompt weights earlier context more heavily.
        return selected;
    }

    static List<String> terms(String query) {
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() >= 3 && !STOP_WORDS.contains(raw)) {
                unique.add(raw);
            }
        }
        return new ArrayList<>(unique);
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int index = text.indexOf(term);
        while (index >= 0 && count < 5) {
            count++;
            index = text.indexOf(term, index + term.length());
        }
        return count;
    }
}
