package com.examprep.api.dto;

/**
 * A stable unit of extracted source material. {@code refId} is the only reference the AI is
 * allowed to cite; {@code locationLabel} is a truthful human-readable location such as
 * "Page 4", "Slide 7" or "Section: Demand curves".
 */
public record DocumentChunk(String refId, int ordinal, String locationLabel, String text) {
}
