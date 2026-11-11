package com.examprep.ai.client;

import java.time.Duration;

/**
 * One AI request. {@code json} selects structured JSON output; the prompt itself must also describe
 * the required JSON shape. {@code timeoutOverride} lets a synchronous call (the tutor) use a shorter
 * per-request timeout than background generation.
 */
public record AiPrompt(
        String systemInstruction,
        String userPrompt,
        boolean json,
        int maxOutputTokens,
        Duration timeoutOverride
) {

    public AiPrompt {
        if (maxOutputTokens < 0) {
            throw new IllegalArgumentException("maxOutputTokens must not be negative");
        }
    }

    public static AiPrompt json(String systemInstruction, String userPrompt, int maxOutputTokens) {
        return new AiPrompt(systemInstruction, userPrompt, true, maxOutputTokens, null);
    }

    public static AiPrompt json(String systemInstruction, String userPrompt, int maxOutputTokens,
                               Duration timeoutOverride) {
        return new AiPrompt(systemInstruction, userPrompt, true, maxOutputTokens, timeoutOverride);
    }

    public static AiPrompt text(String systemInstruction, String userPrompt, int maxOutputTokens) {
        return new AiPrompt(systemInstruction, userPrompt, false, maxOutputTokens, null);
    }
}
