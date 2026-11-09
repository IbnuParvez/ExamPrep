package com.examprep.ai.config;

import com.examprep.ai.exception.AiUnavailableException;
import com.examprep.config.AiProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

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
                    HttpOptions httpOptions = HttpOptions.builder()
                            .timeout(Math.max(10, properties.getTimeoutSeconds()) * 1000)
                            .build();
                    current = Client.builder()
                            .apiKey(properties.getApiKey())
                            .httpOptions(httpOptions)
                            .build();
                    client = current;
                    log.info("Gemini client initialised for model {}", properties.getModel());
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
