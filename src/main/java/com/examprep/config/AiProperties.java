package com.examprep.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the self-hosted FreeLLMAPI OpenAI-compatible router. The proxy key is read from
 * the environment only and is never rendered, logged or returned by an endpoint.
 */
@ConfigurationProperties(prefix = "examprep.ai")
public class AiProperties {

    /** Base URL of the router; {@link #normalizedBaseUrl()} appends the OpenAI {@code /v1} path when absent. */
    private String baseUrl = "http://localhost:3001/v1";
    private String apiKey = "";
    /** Routing mode ("auto", "auto:smart", "auto:fast", "auto:reliable", "auto:balanced", "fusion") or a model ID. */
    private String model = "auto";
    private int connectTimeoutSeconds = 10;
    /** Per-request timeout for background generation. */
    private int timeoutSeconds = 180;
    /** Shorter per-request timeout for synchronous tutor calls. */
    private int tutorTimeoutSeconds = 60;
    private int maxRetries = 2;
    /** Maximum simultaneous router requests; protects the shared proxy key quota. */
    private int maxConcurrency = 3;
    private double temperature = 0.35;
    /** Safe across heterogeneous free providers, unlike provider-specific output ceilings. */
    private int maxOutputTokens = 16_384;
    /** Optional reasoning effort hint forwarded to providers that support it ("low" by default). */
    private String reasoningEffort = "low";
    /** Maximum number of source characters handed to a single generation request. */
    private int maxSourceChars = 90_000;
    /** Target size of a single analysis segment for large documents. */
    private int segmentCharLimit = 18_000;
    private int tutorChunkLimit = 6;
    private int tutorContextChars = 12_000;

    public boolean isConfigured() {
        return hasText(apiKey) && hasText(baseUrl);
    }

    /**
     * Trims trailing slashes and appends {@code /v1} when the URL has no path (or only {@code /}), so
     * both {@code http://localhost:3001} and {@code http://localhost:3001/v1} point at the API.
     */
    public String normalizedBaseUrl() {
        if (baseUrl == null) {
            return "";
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                normalized = normalized + "/v1";
            }
        } catch (IllegalArgumentException malformed) {
            // Left as-is: FreeLlmApiClientFactory reports the malformed URL with actionable guidance.
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getTutorTimeoutSeconds() {
        return tutorTimeoutSeconds;
    }

    public void setTutorTimeoutSeconds(int tutorTimeoutSeconds) {
        this.tutorTimeoutSeconds = tutorTimeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getMaxSourceChars() {
        return maxSourceChars;
    }

    public void setMaxSourceChars(int maxSourceChars) {
        this.maxSourceChars = maxSourceChars;
    }

    public int getSegmentCharLimit() {
        return segmentCharLimit;
    }

    public void setSegmentCharLimit(int segmentCharLimit) {
        this.segmentCharLimit = segmentCharLimit;
    }

    public int getTutorChunkLimit() {
        return tutorChunkLimit;
    }

    public void setTutorChunkLimit(int tutorChunkLimit) {
        this.tutorChunkLimit = tutorChunkLimit;
    }

    public int getTutorContextChars() {
        return tutorContextChars;
    }

    public void setTutorContextChars(int tutorContextChars) {
        this.tutorContextChars = tutorContextChars;
    }
}
