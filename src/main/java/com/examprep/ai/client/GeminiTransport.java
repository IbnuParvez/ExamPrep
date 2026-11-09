package com.examprep.ai.client;

/**
 * Thin abstraction over the SDK call, so retry and error handling can be tested without the
 * network.
 */
public interface GeminiTransport {

    String generateContent(GeminiCall call);

    record GeminiCall(String model,
                      String systemInstruction,
                      String userPrompt,
                      int maxOutputTokens,
                      int timeoutSeconds) {
    }
}
