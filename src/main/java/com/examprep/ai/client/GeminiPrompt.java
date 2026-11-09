package com.examprep.ai.client;

/**
 * One Gemini request. {@code json} selects structured JSON output; the prompt itself must also
 * describe the required JSON shape.
 */
public record GeminiPrompt(String systemInstruction, String userPrompt, boolean json, int maxOutputTokens) {

    public static GeminiPrompt json(String systemInstruction, String userPrompt, int maxOutputTokens) {
        return new GeminiPrompt(systemInstruction, userPrompt, true, maxOutputTokens);
    }

    public static GeminiPrompt text(String systemInstruction, String userPrompt, int maxOutputTokens) {
        return new GeminiPrompt(systemInstruction, userPrompt, false, maxOutputTokens);
    }
}
