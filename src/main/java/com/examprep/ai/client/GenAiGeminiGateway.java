package com.examprep.ai.client;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.examprep.ai.config.GeminiClientFactory;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.config.AiProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;

/**
 * Official Google Gen AI Java SDK implementation. Prompts and completions are never logged: they
 * may contain the student's study material.
 *
 * <p>The SDK's own HTTP retries are switched off in {@link GeminiClientFactory}, so a retry here is
 * exactly one extra HTTP request. Retries are bounded twice: by the configured attempt count and by
 * a wall-clock budget equal to the request timeout. A failure that already consumed the budget (a
 * hung request, for example) is never multiplied, and a fast transient failure (429/5xx) is retried
 * with an exponential backoff long enough to clear a per-minute rate-limit window.
 */
@Component
public class GenAiGeminiGateway implements GeminiGateway {

    private static final Logger log = LoggerFactory.getLogger(GenAiGeminiGateway.class);

    /** A retry is only started when the attempt itself still fits comfortably in the budget. */
    private static final long MINIMUM_ATTEMPT_MILLIS = 5_000L;
    private static final long MAXIMUM_BACKOFF_MILLIS = 60_000L;
    private static final long MAXIMUM_BACKOFF_JITTER_MILLIS = 1_000L;

    private final GeminiClientFactory clientFactory;
    private final AiProperties properties;
    /** One shared key: never let generation jobs and tutor requests burst past the configured limit. */
    private final Semaphore inFlight;

    public GenAiGeminiGateway(GeminiClientFactory clientFactory, AiProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
        // Fair, so a burst of tutor requests cannot starve a queued generation job.
        this.inFlight = new Semaphore(Math.max(1, properties.getMaxConcurrentRequests()), true);
    }

    @Override
    public String generate(GeminiPrompt prompt) {
        Client client = clientFactory.client();
        GenerateContentConfig config = buildConfig(prompt);
        acquirePermit();
        try {
            return generateWithRetries(client, prompt, config);
        } finally {
            inFlight.release();
        }
    }

    private void acquirePermit() {
        try {
            if (!inFlight.tryAcquire(Math.max(10, properties.getTimeoutSeconds()), TimeUnit.SECONDS)) {
                throw new AiServiceException("The AI service is busy with other requests. "
                        + "Please try again in a moment.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
        }
    }

    private String generateWithRetries(Client client, GeminiPrompt prompt, GenerateContentConfig config) {
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        long budgetMillis = perAttemptTimeoutMillis();
        long deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L;

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                GenerateContentResponse response = client.models.generateContent(
                        properties.getModel(), prompt.userPrompt(), config);
                return readText(response).strip();
            } catch (AiResponseException refused) {
                // Deterministic refusal (content policy / recitation): the same prompt is refused again.
                throw refused;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                boolean retryable = isRetryable(failure);
                // Log the root cause and elapsed time (never the prompt or response) so a timeout,
                // a DNS/TLS problem and a rejected key can be told apart from the log alone.
                log.warn("Gemini request failed on attempt {}/{} after {} ms with {} (retryable={}): {}",
                        attempt, attempts, (System.nanoTime() - startedAt) / 1_000_000,
                        failure.getClass().getSimpleName(), retryable, describeCause(failure));
                if (!retryable || attempt == attempts) {
                    break;
                }
                long backoffMillis = backoffMillis(attempt, properties.getRetryBaseDelayMillis());
                if (!retryFitsBudget(deadlineNanos, backoffMillis)) {
                    log.warn("Not retrying the Gemini request: the {} ms budget for all attempts would be exceeded",
                            budgetMillis);
                    break;
                }
                sleepBeforeRetry(backoffMillis);
            }
        }
        if (lastFailure instanceof ResponseProblem problem) {
            throw new AiServiceException(problem.userMessage(), lastFailure);
        }
        throw new AiServiceException(friendlyMessage(lastFailure), lastFailure);
    }

    private GenerateContentConfig buildConfig(GeminiPrompt prompt) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(prompt.systemInstruction())))
                .responseMimeType(prompt.json() ? "application/json" : "text/plain")
                // temperature is intentionally not sent: newer Gemini 3.x models manage sampling themselves.
                .maxOutputTokens(prompt.maxOutputTokens() > 0
                        ? prompt.maxOutputTokens()
                        : properties.getMaxOutputTokens());
        ThinkingConfig thinkingConfig = thinkingConfig();
        if (thinkingConfig != null) {
            builder.thinkingConfig(thinkingConfig);
        }
        return builder.build();
    }

    /**
     * Gemini 3.x models spend output tokens on internal reasoning before any answer is emitted, so
     * an explicit thinking level (low by default) keeps most of the output budget for the answer.
     */
    private ThinkingConfig thinkingConfig() {
        String configured = properties.getThinkingLevel();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        // MINIMAL is deliberately not offered: gemini-3.8-flash rejects it with HTTP 400.
        ThinkingLevel.Known level = switch (configured.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> ThinkingLevel.Known.LOW;
            case "MEDIUM" -> ThinkingLevel.Known.MEDIUM;
            case "HIGH" -> ThinkingLevel.Known.HIGH;
            default -> {
                log.warn("Ignoring examprep.ai.thinking-level '{}': use low, medium or high", configured);
                yield null;
            }
        };
        return level == null ? null : ThinkingConfig.builder().thinkingLevel(level).build();
    }

    /**
     * Turns a provider response into text, or explains why it cannot be used. An empty or truncated
     * answer is worth one more attempt; a content-policy refusal is not.
     */
    private String readText(GenerateContentResponse response) {
        if (response == null) {
            throw new ResponseProblem("Gemini returned no response at all. Please try again.");
        }
        FinishReason finishReason = response.finishReason();
        FinishReason.Known reason = finishReason == null ? null : finishReason.knownEnum();
        if (reason == FinishReason.Known.MAX_TOKENS) {
            throw new ResponseProblem("Gemini stopped because it reached the output-token limit before the "
                    + "answer was complete. Raise GEMINI_MAX_OUTPUT_TOKENS or ask for a smaller result.");
        }
        if (reason != null && isContentRefusal(reason)) {
            throw new AiResponseException("Gemini refused to answer because of its content policy (" + reason
                    + "). Rewording the question or using different study material may help; retrying the "
                    + "same request will not.");
        }
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new ResponseProblem("Gemini returned an empty response. This is usually momentary; "
                    + "please try again.");
        }
        return text;
    }

    private static boolean isContentRefusal(FinishReason.Known reason) {
        return switch (reason) {
            case SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY,
                    IMAGE_PROHIBITED_CONTENT, IMAGE_RECITATION -> true;
            default -> false;
        };
    }

    private long perAttemptTimeoutMillis() {
        return Math.max(10, properties.getTimeoutSeconds()) * 1000L;
    }

    private static boolean retryFitsBudget(long deadlineNanos, long backoffMillis) {
        long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        return remainingMillis > backoffMillis + MINIMUM_ATTEMPT_MILLIS;
    }

    /** Exponential backoff with jitter: base, 2x base, 4x base, ... capped. */
    static long backoffMillis(int attempt, long baseMillis) {
        long base = Math.min(Math.max(1L, baseMillis), MAXIMUM_BACKOFF_MILLIS);
        long exponential = base << Math.min(Math.max(0, attempt - 1), 4);
        return Math.min(exponential, MAXIMUM_BACKOFF_MILLIS)
                + ThreadLocalRandom.current().nextLong(0, MAXIMUM_BACKOFF_JITTER_MILLIS + 1);
    }

    private void sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
        }
    }

    /** Transient means "the same request may well succeed in a moment". */
    static boolean isRetryable(RuntimeException failure) {
        if (failure instanceof ResponseProblem) {
            return true;
        }
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiFailure) {
                // Decisive: an SDK status code is more reliable than anything in the message text.
                return isRetryableApiFailure(apiFailure.code(), apiFailure.status());
            }
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isRetryableApiFailure(int code, String status) {
        if (code == 408 || code == 409 || code == 429 || code >= 500) {
            return true;
        }
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "DEADLINE_EXCEEDED", "ABORTED" -> true;
            default -> false;
        };
    }

    private String friendlyMessage(RuntimeException failure) {
        ApiException apiFailure = findApiFailure(failure);
        if (apiFailure != null) {
            int code = apiFailure.code();
            String status = apiFailure.status() == null ? "" : apiFailure.status().trim();
            if (code == 429 || "RESOURCE_EXHAUSTED".equalsIgnoreCase(status)) {
                return "Gemini is rate limiting requests right now. Wait a moment, then try again.";
            }
            if (code == 401 || code == 403 || "PERMISSION_DENIED".equalsIgnoreCase(status)) {
                return "Gemini rejected the configured API key. Check GOOGLE_API_KEY and that the Gemini API "
                        + "is enabled.";
            }
            if (code == 404) {
                return "The configured Gemini model is not available to this API key. Check GEMINI_MODEL.";
            }
            if (code == 408 || code >= 500) {
                return "Gemini was unavailable or did not respond in time. Try again, or upload a shorter "
                        + "document.";
            }
            return "Gemini rejected the request (" + code + (status.isBlank() ? "" : " " + status)
                    + "). Check the server log for the provider's explanation.";
        }
        String message = collectMessages(failure).toLowerCase(Locale.ROOT);
        if (message.contains("quota") || message.contains("rate limit") || message.contains("too many requests")
                || message.contains("overloaded")) {
            return "Gemini is rate limiting requests right now. Wait a moment, then try again.";
        }
        if (message.contains("deadline") || message.contains("timed out") || message.contains("timeout")) {
            return "Gemini did not respond in time. Try again, or upload a shorter document.";
        }
        return "Gemini could not be reached or returned an error. Please try again shortly.";
    }

    private static ApiException findApiFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiFailure) {
                return apiFailure;
            }
            current = current.getCause();
        }
        return null;
    }

    private String describeCause(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) {
                builder.append(" <- ");
            }
            String message = current.getMessage();
            if (message != null && message.length() > 200) {
                message = message.substring(0, 200) + "...";
            }
            builder.append(current.getClass().getSimpleName()).append(": ").append(message);
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private String collectMessages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 5) {
            String message = current.getMessage();
            if (message != null) {
                builder.append(message).append(' ');
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    /** A response Gemini produced that is unusable, but that a regeneration may well fix. */
    private static final class ResponseProblem extends RuntimeException {

        private final String userMessage;

        ResponseProblem(String userMessage) {
            super(userMessage, null, false, false);
            this.userMessage = userMessage;
        }

        String userMessage() {
            return userMessage;
        }
    }
}
