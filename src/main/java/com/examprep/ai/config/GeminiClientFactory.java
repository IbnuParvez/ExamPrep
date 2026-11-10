package com.examprep.ai.config;

import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.config.AiProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates the official Google Gen AI client on first use. The API key is read from configuration
 * and is never logged, rendered or returned to the browser.
 */
@Component
public class GeminiClientFactory {

    private static final Logger log = LoggerFactory.getLogger(GeminiClientFactory.class);

    private final AiProperties properties;
    private volatile Client client;

    public GeminiClientFactory(AiProperties properties) {
        this.properties = properties;
    }

    public Client client() {
        if (!properties.isConfigured()) {
            throw new AiUnavailableException("The Gemini API is not configured on this server. "
                    + "Ask the administrator to set the GOOGLE_API_KEY environment variable, then try again.");
        }
        Client current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    // timeout() becomes OkHttp's whole-call callTimeout, which spans every attempt the SDK
                    // makes internally. The SDK's RetryInterceptor retries on its own by default, so leaving
                    // retryOptions unset multiplied our retry loop by the SDK's (up to 15 HTTP calls per
                    // prompt) and let one callTimeout kill a healthy retry mid-stream. Retries belong to
                    // GenAiGeminiGateway: exactly one HTTP attempt per call here, each bounded by the
                    // configured timeout, with explicit backoff and a total time budget above it.
                    HttpOptions httpOptions = HttpOptions.builder()
                            .timeout(Math.max(10, properties.getTimeoutSeconds()) * 1000)
                            .retryOptions(HttpRetryOptions.builder()
                                    .attempts(1)
                                    .build())
                            .build();
                    current = Client.builder()
                            .apiKey(properties.getApiKey())
                            .httpOptions(httpOptions)
                            .build();
                    client = current;
                    log.info("Gemini client initialised for model {} (one HTTP attempt per call, retries handled by the gateway)",
                            properties.getModel());
                }
            }
        }
        return current;
    }

    @PreDestroy
    void close() {
        Client current = client;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException exception) {
                log.debug("Could not close Gemini client cleanly");
            }
        }
    }
}
