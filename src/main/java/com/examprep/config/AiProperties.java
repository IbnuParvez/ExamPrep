package com.examprep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini configuration. The API key is read from the environment only and is never rendered,
 * logged or returned by an endpoint.
 */
@ConfigurationProperties(prefix = "examprep.ai")
public class AiProperties {

    private String apiKey = "";
    private String model = "gemini-3.8-flash";
    private int timeoutSeconds = 240;
    private int maxRetries = 2;
    /** Base delay before retrying a transient failure; doubled on every further attempt. */
    private int retryBaseDelayMillis = 5_000;
    /** Maximum simultaneous Gemini requests; protects the shared API key quota. */
    private int maxConcurrentRequests = 4;
    private double temperature = 0.35;
    /**
     * Ceiling for a single answer, including the model's internal thinking tokens. gemini-3.8-flash
     * supports 65536; at 32768 the thinking pass regularly consumed the whole budget before any
     * answer (or JSON) was emitted, which surfaced as an empty or truncated response.
     */
    private int maxOutputTokens = 65_536;
    /** Thinking effort for Gemini 3.x models: {@code low}, {@code medium} or {@code high}. */
    private String thinkingLevel = "low";
    /** Maximum number of source characters handed to a single generation request. */
    private int maxSourceChars = 90_000;
    /** Target size of a single analysis segment for large documents. */
    private int segmentCharLimit = 18_000;
    private int tutorChunkLimit = 6;
    private int tutorContextChars = 12_000;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRetryBaseDelayMillis() {
        return retryBaseDelayMillis;
    }

    public void setRetryBaseDelayMillis(int retryBaseDelayMillis) {
        this.retryBaseDelayMillis = retryBaseDelayMillis;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
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

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
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
