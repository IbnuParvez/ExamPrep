package com.examprep.ai.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.config.AiProperties;

/**
 * Creates the shared JDK {@link HttpClient} on first use. The proxy key is read from configuration
 * and is never logged, rendered or returned to the browser.
 */
@Component
public class FreeLlmApiClientFactory {

    private static final Logger log = LoggerFactory.getLogger(FreeLlmApiClientFactory.class);

    private final AiProperties properties;
    private volatile HttpClient client;
    private volatile URI baseUri;

    public FreeLlmApiClientFactory(AiProperties properties) {
        this.properties = properties;
    }

    public HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    baseUri = requireBaseUri();
                    current = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                            // Redirects could forward the Authorization header to another origin.
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    client = current;
                    log.info("FreeLLMAPI client initialised for model {} at {}", modelName(), baseUri);
                }
            }
        }
        return current;
    }

    /** The OpenAI-compatible chat completions endpoint derived from the configured base URL. */
    public URI endpoint() {
        return URI.create(baseUri() + "/chat/completions");
    }

    public URI baseUri() {
        URI current = baseUri;
        if (current == null) {
            client();
            current = baseUri;
        }
        return current;
    }

    private URI requireBaseUri() {
        if (!properties.isConfigured()) {
            throw new AiUnavailableException("The AI service is not configured on this server. "
                    + "Ask the administrator to set the FREELLMAPI_API_KEY and FREELLMAPI_BASE_URL "
                    + "environment variables, then try again.");
        }
        String normalized = properties.normalizedBaseUrl();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException malformed) {
            throw new AiUnavailableException("FREELLMAPI_BASE_URL is not a valid URL. Ask the administrator "
                    + "to correct it, then try again.");
        }
        String scheme = uri.getScheme();
        boolean supportedScheme = scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        if (!supportedScheme || uri.getHost() == null) {
            throw new AiUnavailableException("FREELLMAPI_BASE_URL must be an absolute http or https URL. "
                    + "Ask the administrator to correct it, then try again.");
        }
        return URI.create(normalized);
    }

    private String modelName() {
        return properties.getModel() == null || properties.getModel().isBlank()
                ? "auto" : properties.getModel().strip();
    }
}
