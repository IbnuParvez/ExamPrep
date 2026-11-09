package com.examprep.ai.retrieval;

import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.SourceReferenceDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates every citation returned by the model against the chunks that were actually sent.
 * Invalid references are discarded rather than shown to the student.
 */
@Component
public class SourceReferenceResolver {

    public List<SourceReferenceDto> resolve(List<String> requestedReferences, List<DocumentChunkDto> chunks) {
        if (requestedReferences == null || requestedReferences.isEmpty()) {
            return List.of();
        }
        Map<String, DocumentChunkDto> byReference = index(chunks);
        Set<String> seen = new LinkedHashSet<>();
        List<SourceReferenceDto> resolved = new ArrayList<>();
        for (String requested : requestedReferences) {
            String key = normalize(requested);
            if (key == null || !seen.add(key)) {
                continue;
            }
            DocumentChunkDto chunk = byReference.get(key);
            if (chunk != null) {
                resolved.add(new SourceReferenceDto(chunk.referenceId(), chunk.locationLabel()));
            }
        }
        return List.copyOf(resolved);
    }

    /**
     * Resolves a single citation to its human readable location, or {@code null} when the model
     * referenced something that was not supplied.
     */
    public String resolveLocation(String requestedReference, List<DocumentChunkDto> chunks) {
        String key = normalize(requestedReference);
        if (key == null) {
            return null;
        }
        DocumentChunkDto chunk = index(chunks).get(key);
        return chunk == null ? null : chunk.locationLabel();
    }

    private static Map<String, DocumentChunkDto> index(List<DocumentChunkDto> chunks) {
        Map<String, DocumentChunkDto> byReference = new LinkedHashMap<>();
        for (DocumentChunkDto chunk : chunks) {
            byReference.put(normalize(chunk.referenceId()), chunk);
        }
        return byReference;
    }

    private static String normalize(String reference) {
        if (reference == null) {
            return null;
        }
        String value = reference.trim().toUpperCase(Locale.ROOT);
        // Models occasionally wrap identifiers, for example "[S12]" or "S12.".
        value = value.replaceAll("^[\\[(\"'#]+", "").replaceAll("[\\])\"'.,;:]+$", "").trim();
        return value.isEmpty() ? null : value;
    }
}
