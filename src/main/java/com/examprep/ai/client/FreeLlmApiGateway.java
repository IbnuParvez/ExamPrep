package com.examprep.ai.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.examprep.ai.config.FreeLlmApiClientFactory;
import com.examprep.ai.exception.AiRateLimitException;
import com.examprep.ai.exception.AiResponseException;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.config.AiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAI-compatible client for the self-hosted FreeLLMAPI router. Prompts, completions and the proxy
 * key are never logged: they may contain the student's study material and credentials.
 *
 * <p>Retries are bounded by the configured attempt count. Only failures that a moment's wait could
 * fix are retried (empty completions, timeouts, I/O errors and 408/5xx); an unsupported request
 * argument triggers exactly one retry with a minimal payload, and a rejected key or an exhausted
 * quota fails immediately.
 */
@Component
public class FreeLlmApiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(FreeLlmApiGateway.class);

    /** Providers behind the router may emit a leading reasoning block that is not part of the answer. */
    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)^\\s*<think(?:ing)?[^>]*>.*?</think>\\s*");
    private static final Pattern UNCLOSED_THINK_BLOCK = Pattern.compile("(?is)^\\s*<think(?:ing)?[^>]*>.*$");

    private static final long RETRY_BASE_DELAY_MILLIS = 1_000L;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000L;
    private static final long MAX_RETRY_AFTER_JITTER_MILLIS = 250L;
    /** A longer Retry-After is treated as quota exhaustion rather than blocking a request thread. */
    private static final long MAX_ACCEPTABLE_RETRY_AFTER_SECONDS = 15L;

    private final FreeLlmApiClientFactory clientFactory;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    /** One shared key: never let generation jobs and tutor requests burst past the configured limit. */
    private final Semaphore inFlight;

    public FreeLlmApiGateway(FreeLlmApiClientFactory clientFactory, AiProperties properties,
                             ObjectMapper objectMapper) {
        this.clientFactory = clientFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
        // Fair, so a burst of tutor requests cannot starve a queued generation job.
        this.inFlight = new Semaphore(Math.max(1, properties.getMaxConcurrency()), true);
    }

    @Override
    public String generate(AiPrompt prompt) {
        HttpClient client = clientFactory.client();
        URI endpoint = clientFactory.endpoint();
        Duration timeout = timeoutFor(prompt);
        acquirePermit(timeout);
        try {
            return callWithRetries(client, endpoint, prompt, timeout);
        } finally {
            inFlight.release();
        }
    }

    private Duration timeoutFor(AiPrompt prompt) {
        Duration override = prompt.timeoutOverride();
        if (override != null && !override.isNegative() && !override.isZero()) {
            return override;
        }
        return Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds()));
    }

    private void acquirePermit(Duration timeout) {
        try {
            if (!inFlight.tryAcquire(Math.max(10, timeout.toSeconds()), TimeUnit.SECONDS)) {
                throw new AiServiceException("The AI service is busy with other requests. "
                        + "Please try again in a moment.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
        }
    }

    private String callWithRetries(HttpClient client, URI endpoint, AiPrompt prompt, Duration timeout) {
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        int attempt = 0;
        boolean minimalPayload = false;
        boolean minimalPayloadTried = false;
        while (true) {
            attempt++;
            long startedAt = System.nanoTime();
            try {
                HttpResponse<String> response = send(client, endpoint, prompt, minimalPayload, timeout);
                int status = response.statusCode();
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                String routedVia = safeHeader(response, "X-Routed-Via");
                String fallbackAttempts = safeHeader(response, "X-Fallback-Attempts");
                // Only routing metadata, status and timing are safe to log; never prompts or completions.
                log.info("FreeLLMAPI request status={} attempt={} elapsedMs={} routedVia={} fallbackAttempts={}",
                        status, attempt, elapsedMillis, routedVia, fallbackAttempts);
                if (status >= 200 && status < 300) {
                    return readContent(response.body(), prompt);
                }
                if (status == 401 || status == 403) {
                    throw new AiServiceException("The AI router rejected the configured proxy key. "
                            + "Check FREELLMAPI_API_KEY and restart the application.");
                }
                if (status == 400 || status == 422) {
                    Failure failure = readFailure(response.body());
                    if (isUnsupportedArguments(failure)) {
                        if (minimalPayloadTried) {
                            throw new AiServiceException("The AI router rejected the request arguments even "
                                    + "after retrying with a minimal OpenAI payload. Check FREELLMAPI_MODEL.");
                        }
                        minimalPayloadTried = true;
                        minimalPayload = true;
                        continue;
                    }
                    if (status == 422 && isNoModel(failure)) {
                        throw new AiServiceException("The AI router has no usable model configured. Enable at "
                                + "least one provider key and model in the FreeLLMAPI dashboard, then try again.");
                    }
                    throw new AiServiceException("The AI router rejected the request (HTTP " + status + "). "
                            + "Check the FreeLLMAPI dashboard and FREELLMAPI_MODEL configuration.");
                }
                if (status == 429) {
                    Failure failure = readFailure(response.body());
                    Long retryAfterSeconds = retryAfterSeconds(response);
                    boolean quotaExhausted = "quota_exceeded".equalsIgnoreCase(failure.code());
                    boolean tooLongToWait = retryAfterSeconds != null
                            && retryAfterSeconds > MAX_ACCEPTABLE_RETRY_AFTER_SECONDS;
                    if (quotaExhausted || tooLongToWait) {
                        throw new AiRateLimitException("The AI router is rate limiting requests or has used "
                                + "its quota. Wait a while or enable another provider in the FreeLLMAPI dashboard.");
                    }
                    if (attempt >= maxAttempts) {
                        throw new AiRateLimitException("The AI router is rate limiting requests right now. "
                                + "Wait a moment, then try again.");
                    }
                    long waitMillis = retryAfterSeconds != null && retryAfterSeconds > 0
                            ? retryAfterSeconds * 1000L : backoffMillis(attempt);
                    sleepBeforeRetry(waitMillis);
                    continue;
                }
                if (status == 408 || status == 500 || status == 502 || status == 503 || status == 504) {
                    if (attempt >= maxAttempts) {
                        throw new AiServiceException("The AI router was unavailable or did not respond in time. "
                                + "Try again, or upload a shorter document.");
                    }
                    sleepBeforeRetry(backoffMillis(attempt));
                    continue;
                }
                throw new AiServiceException("The AI router returned an unexpected response (HTTP " + status
                        + "). Please try again shortly.");
            } catch (TransientFailure transientFailure) {
                log.warn("FreeLLMAPI returned an unusable completion on attempt {}/{}", attempt, maxAttempts);
                if (attempt >= maxAttempts) {
                    throw new AiServiceException(transientFailure.getMessage() + " Please try again.", transientFailure);
                }
                sleepBeforeRetry(backoffMillis(attempt));
            } catch (HttpTimeoutException timeoutFailure) {
                log.warn("FreeLLMAPI request timed out on attempt {}/{} after {} ms",
                        attempt, maxAttempts, (System.nanoTime() - startedAt) / 1_000_000L);
                if (attempt >= maxAttempts) {
                    throw new AiServiceException("The AI router did not respond in time. Try again, or upload "
                            + "a shorter document.", timeoutFailure);
                }
                sleepBeforeRetry(backoffMillis(attempt));
            } catch (IOException ioFailure) {
                log.warn("FreeLLMAPI request failed on attempt {}/{} after {} ms with {}",
                        attempt, maxAttempts, (System.nanoTime() - startedAt) / 1_000_000L,
                        ioFailure.getClass().getSimpleName());
                if (attempt >= maxAttempts) {
                    throw new AiServiceException("The AI router could not be reached. Check FREELLMAPI_BASE_URL "
                            + "and that the router is running, then try again.", ioFailure);
                }
                sleepBeforeRetry(backoffMillis(attempt));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
            }
        }
    }

    private HttpResponse<String> send(HttpClient client, URI endpoint, AiPrompt prompt, boolean minimalPayload,
                                      Duration timeout) throws IOException, InterruptedException {
        String body = payload(prompt, minimalPayload);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.getApiKey().strip())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String payload(AiPrompt prompt, boolean minimalPayload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.systemInstruction() == null
                        ? "" : prompt.systemInstruction()),
                Map.of("role", "user", "content", prompt.userPrompt() == null ? "" : prompt.userPrompt())));
        body.put("temperature", properties.getTemperature());
        if (!minimalPayload) {
            int maxTokens = prompt.maxOutputTokens() > 0
                    ? prompt.maxOutputTokens() : properties.getMaxOutputTokens();
            body.put("max_tokens", maxTokens);
            if (prompt.json()) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
                body.put("reasoning_effort", properties.getReasoningEffort().strip());
            }
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new AiServiceException("The AI request could not be prepared. Please try again.", exception);
        }
    }

    /**
     * Turns a completion into text, or explains why it cannot be used. A provider reasoning block is
     * stripped first, because it is not part of the answer (and would break JSON parsing).
     */
    private String readContent(String body, AiPrompt prompt) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (JsonProcessingException exception) {
            throw new AiResponseException("The AI router returned a response that could not be read. "
                    + "Please try again.", exception);
        }
        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("");
        if ("content_filter".equalsIgnoreCase(finishReason)) {
            throw new AiServiceException("The AI provider refused to answer because of its content policy. "
                    + "Rewording the question or using different study material may help; retrying the same "
                    + "request will not.");
        }
        String content = textOf(choice.path("message").path("content"));
        String cleaned = stripReasoningBlock(content).strip();
        if (cleaned.isEmpty()) {
            throw new TransientFailure("The AI router returned an empty completion.");
        }
        if ("length".equalsIgnoreCase(finishReason) && prompt.json() && !isCompleteJson(cleaned)) {
            throw new AiResponseException("The AI model stopped at its output-token limit before the JSON was "
                    + "complete. Please try again, or generate the package from a shorter document.");
        }
        return cleaned;
    }

    /** Accepts both {@code "content": "..."} and {@code "content": [{"type":"text","text":"..."}]}. */
    static String textOf(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder combined = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    combined.append(part.asText());
                } else if (part.isObject() && part.path("text").isTextual()) {
                    combined.append(part.path("text").asText());
                }
            }
            return combined.toString();
        }
        return "";
    }

    static String stripReasoningBlock(String content) {
        if (content == null) {
            return "";
        }
        String stripped = THINK_BLOCK.matcher(content).replaceFirst("");
        if (stripped.stripLeading().toLowerCase(Locale.ROOT).startsWith("<think")) {
            stripped = UNCLOSED_THINK_BLOCK.matcher(stripped).replaceFirst("");
        }
        return stripped;
    }

    private boolean isCompleteJson(String content) {
        try {
            return objectMapper.readTree(content) != null;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String modelName() {
        return properties.getModel() == null || properties.getModel().isBlank()
                ? "auto" : properties.getModel().strip();
    }

    private static boolean isUnsupportedArguments(Failure failure) {
        String combined = (failure.code() + " " + failure.message()).toLowerCase(Locale.ROOT);
        boolean mentionsArgument = combined.contains("max_tokens") || combined.contains("response_format")
                || combined.contains("reasoning_effort");
        boolean unsupported = combined.contains("unsupported") || combined.contains("unrecognized")
                || combined.contains("unknown") || combined.contains("not supported")
                || combined.contains("extra fields") || combined.contains("invalid_request");
        return mentionsArgument && unsupported;
    }

    private static boolean isNoModel(Failure failure) {
        String combined = (failure.code() + " " + failure.message()).toLowerCase(Locale.ROOT);
        return combined.contains("no_model") || combined.contains("no model")
                || combined.contains("no enabled model") || combined.contains("fallback chain")
                || combined.contains("empty fallback");
    }

    private Failure readFailure(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            JsonNode error = root.path("error");
            String code = error.path("code").asText("");
            if (code.isBlank()) {
                code = root.path("code").asText("");
            }
            String message = error.path("message").asText("");
            if (message.isBlank()) {
                message = root.path("message").asText("");
            }
            return new Failure(code, message);
        } catch (JsonProcessingException exception) {
            return new Failure("", "");
        }
    }

    private static Long retryAfterSeconds(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(value.strip()));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime at = ZonedDateTime.parse(value.strip(), DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0L, Duration.between(Instant.now(), at.toInstant()).toSeconds());
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }

    private static String safeHeader(HttpResponse<?> response, String name) {
        String value = response.headers().firstValue(name).orElse(null);
        if (value == null) {
            return "-";
        }
        String trimmed = value.strip();
        if (trimmed.length() > 120) {
            trimmed = trimmed.substring(0, 120);
        }
        return trimmed.isBlank() ? "-" : trimmed;
    }

    /** Exponential backoff with jitter: base, 2x base, 4x base, ... capped. */
    static long backoffMillis(int attempt) {
        long exponential = RETRY_BASE_DELAY_MILLIS << Math.min(Math.max(0, attempt - 1), 4);
        return Math.min(exponential, MAX_RETRY_DELAY_MILLIS)
                + ThreadLocalRandom.current().nextLong(0, MAX_RETRY_AFTER_JITTER_MILLIS + 1);
    }

    private void sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", interrupted);
        }
    }

    private record Failure(String code, String message) {
    }

    /** A completion the provider produced that is unusable, but that a regeneration may well fix. */
    private static final class TransientFailure extends RuntimeException {

        TransientFailure(String message) {
            super(message, null, false, false);
        }
    }
}
