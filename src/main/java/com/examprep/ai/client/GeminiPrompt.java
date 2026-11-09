package com.examprep.ai.client;

/**
 * A single model request. The system instruction is always trusted application text; the user
 * prompt contains the delimited, untrusted study material.
 */
public record GeminiPrompt(String systemInstruction, String userPrompt, int maxOutputTokens) {

    public GeminiPrompt withUserPrompt(String newUserPrompt) {
        return new GeminiPrompt(systemInstruction, newUserPrompt, maxOutputTokens);
    }
}
