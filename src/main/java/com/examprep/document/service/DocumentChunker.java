package com.examprep.document.service;

import com.examprep.config.AppProperties;
import com.examprep.document.parser.ExtractedDocument;
import com.examprep.document.parser.ExtractedSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Splits extracted sections into chunks of a bounded size while keeping the source location of
 * every chunk. Reference identifiers ({@code S1}, {@code S2}, ...) are stable for a processed
 * document and are the only citation identifiers accepted from the AI model.
 */
@Component
public class DocumentChunker {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final int MAX_LOCATION_LABEL = 160;
    private static final int MAX_HEADING = 300;

    private final int maxChunkCharacters;
    private final int targetChunkCharacters;

    public DocumentChunker(AppProperties properties) {
        this.maxChunkCharacters = Math.max(400, properties.processing().maxChunkCharacters());
        this.targetChunkCharacters = Math.max(200,
                Math.min(properties.processing().targetChunkCharacters(), maxChunkCharacters));
    }

    public List<ChunkDraft> chunk(ExtractedDocument document) {
        List<ChunkDraft> chunks = new ArrayList<>();
        for (ExtractedSection section : document.sections()) {
            String location = truncate(section.locationLabel(), MAX_LOCATION_LABEL);
            String heading = section.heading() == null ? null : truncate(section.heading(), MAX_HEADING);
            for (String piece : splitSection(section.text())) {
                String text = piece.strip();
                if (text.isEmpty()) {
                    continue;
                }
                String referenceId = "S" + (chunks.size() + 1);
                chunks.add(new ChunkDraft(referenceId, location, heading, text));
            }
        }
        return chunks;
    }

    List<String> splitSection(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\n")) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > maxChunkCharacters) {
                flush(pieces, current);
                pieces.addAll(splitLongParagraph(trimmed));
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() + 1 > maxChunkCharacters) {
                flush(pieces, current);
            }
            current.append(trimmed).append('\n');
        }
        flush(pieces, current);
        return pieces;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_BOUNDARY.split(paragraph)) {
            if (sentence.length() > maxChunkCharacters) {
                flush(pieces, current);
                pieces.addAll(hardSplit(sentence));
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() + 1 > targetChunkCharacters) {
                flush(pieces, current);
            }
            current.append(sentence).append(' ');
        }
        flush(pieces, current);
        return pieces;
    }

    private List<String> hardSplit(String text) {
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length(); start += targetChunkCharacters) {
            pieces.add(text.substring(start, Math.min(text.length(), start + targetChunkCharacters)));
        }
        return pieces;
    }

    private static void flush(List<String> pieces, StringBuilder current) {
        String text = current.toString().strip();
        if (!text.isEmpty()) {
            pieces.add(text);
        }
        current.setLength(0);
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }
}
