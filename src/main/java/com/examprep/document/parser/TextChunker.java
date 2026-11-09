package com.examprep.document.parser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.examprep.api.dto.DocumentChunk;

/**
 * Splits extracted text into citable chunks. Chunks never cross a page, slide or section
 * boundary, so a chunk's location label is always truthful.
 */
@Component
public class TextChunker {

    static final int TARGET_CHARS = 1400;
    static final int MAX_CHARS = 1900;
    static final int MIN_BLOCK_CHARS = 12;

    public List<DocumentChunk> chunk(List<ExtractedBlock> blocks) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int ordinal = 0;
        for (ExtractedBlock block : blocks) {
            String text = normalize(block.text());
            if (text.length() < MIN_BLOCK_CHARS) {
                continue;
            }
            String label = block.locationLabel() == null || block.locationLabel().isBlank()
                    ? "Document text"
                    : block.locationLabel();
            for (String piece : split(text)) {
                if (piece.length() < MIN_BLOCK_CHARS) {
                    continue;
                }
                ordinal++;
                chunks.add(new DocumentChunk("c" + ordinal, ordinal, label, piece));
            }
        }
        return chunks;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace('\u00a0', ' ');
        text = text.replaceAll("[\\t\\x0B\\f]+", " ");
        text = text.replaceAll(" +", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    /** Greedy split on sentence/paragraph boundaries, never exceeding {@link #MAX_CHARS}. */
    static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int remaining = text.length() - start;
            if (remaining <= MAX_CHARS) {
                pieces.add(text.substring(start).strip());
                break;
            }
            int windowEnd = Math.min(start + TARGET_CHARS + 400, text.length());
            int cut = findBoundary(text, start + TARGET_CHARS, windowEnd);
            if (cut <= start) {
                cut = start + TARGET_CHARS;
            }
            pieces.add(text.substring(start, cut).strip());
            start = cut;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        pieces.removeIf(String::isBlank);
        return pieces;
    }

    private static int findBoundary(String text, int from, int to) {
        int best = -1;
        for (int index = from; index < to; index++) {
            char character = text.charAt(index);
            if (character == '\n' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                return index + 2;
            }
            if (character == '.' || character == '!' || character == '?') {
                best = index + 1;
            }
        }
        return best > 0 ? best : -1;
    }
}
