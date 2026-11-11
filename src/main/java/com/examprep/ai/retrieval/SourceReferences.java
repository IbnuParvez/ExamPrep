package com.examprep.ai.retrieval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.examprep.api.dto.DocumentChunk;

/**
 * Maps AI-returned chunk reference IDs to real document locations. Any reference that does not
 * exist in the supplied source set is discarded, so fabricated citations never reach the browser.
 */
public final class SourceReferences {

    private static final Pattern REFERENCE = Pattern.compile("\\bc\\d+\\b", Pattern.CASE_INSENSITIVE);
    /** flashcards.source_ref and questions.source_ref are VARCHAR(200) in the database. */
    private static final int MAX_LABEL_CHARS = 200;

    private final Map<String, String> labels;

    private SourceReferences(Map<String, String> labels) {
        this.labels = labels;
    }

    public static SourceReferences from(Collection<DocumentChunk> chunks) {
        Map<String, String> labels = new TreeMap<>();
        for (DocumentChunk chunk : chunks) {
            labels.put(normalize(chunk.refId()), chunk.locationLabel());
        }
        return new SourceReferences(labels);
    }

    public boolean knows(String refId) {
        return labels.containsKey(normalize(refId));
    }

    /**
     * Resolves one value that may contain multiple reference IDs into display labels, capped at the
     * stored column width so multi-chunk DOCX section labels never overflow {@code VARCHAR(200)}.
     */
    public String resolveToLabel(String value) {
        List<String> resolved = resolveAll(List.of(value == null ? "" : value));
        if (resolved.isEmpty()) {
            return null;
        }
        String label = String.join(", ", resolved);
        return label.length() <= MAX_LABEL_CHARS ? label : label.substring(0, MAX_LABEL_CHARS).strip();
    }

    /** Resolves reference IDs to distinct location labels, dropping fabricated ones. */
    public List<String> resolveAll(Collection<String> values) {
        Set<String> resolved = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            Matcher matcher = REFERENCE.matcher(value);
            while (matcher.find()) {
                String label = labels.get(normalize(matcher.group()));
                if (label != null) {
                    resolved.add(label);
                }
            }
        }
        return new ArrayList<>(resolved);
    }

    public Set<String> knownReferences() {
        return labels.keySet();
    }

    private static String normalize(String refId) {
        if (refId == null) {
            return "";
        }
        return refId.trim().toLowerCase().replace("[", "").replace("]", "");
    }

    public String describeKnown() {
        return labels.keySet().stream().limit(12).collect(Collectors.joining(", "));
    }
}
