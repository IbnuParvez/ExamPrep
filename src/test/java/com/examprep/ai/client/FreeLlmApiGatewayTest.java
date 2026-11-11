package com.examprep.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.examprep.ai.config.FreeLlmApiClientFactory;
import com.examprep.ai.exception.AiRateLimitException;
import com.examprep.ai.exception.AiServiceException;
import com.examprep.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** Exercises the real HTTP contract against a tiny local OpenAI-compatible server. */
class FreeLlmApiGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<String> authorizationHeaders = new ArrayList<>();
    private final List<JsonNode> requestBodies = new ArrayList<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    private HttpServer server;
    private FreeLlmApiGateway gateway;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            lastPath.set(exchange.getRequestURI().getPath());
            requestBodies.add(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", null, null) : replies.removeFirst();
            if (reply.headerName() != null) {
                exchange.getResponseHeaders().add(reply.headerName(), reply.headerValue());
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setApiKey("test-proxy-key");
        properties.setMaxRetries(1);
        properties.setTimeoutSeconds(10);
        properties.setConnectTimeoutSeconds(2);
        gateway = new FreeLlmApiGateway(new FreeLlmApiClientFactory(properties), properties, objectMapper);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerAuthAndOpenAiJsonAndStripsReasoningBlock() {
        enqueue(200, completion("{\"title\":\"ok\"}"), null, null);

        String result = gateway.generate(AiPrompt.json("You are a tutor.", "Summarise this.", 512));

        assertThat(result).isEqualTo("{\"title\":\"ok\"}");
        assertThat(lastPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorizationHeaders).containsExactly("Bearer test-proxy-key");

        JsonNode body = requestBodies.get(0);
        assertThat(body.path("model").asText()).isEqualTo("auto");
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").asText()).isEqualTo("You are a tutor.");
        assertThat(body.path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.35);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
    }

    @Test
    void acceptsArrayContentAndStripsLeadingThinkBlock() {
        enqueue(200, completion("<think reasoning>hidden reasoning</think>\nANSWER"), null, null);

        String result = gateway.generate(AiPrompt.text("system", "question", 100));

        assertThat(result).isEqualTo("ANSWER");
    }

    @Test
    void retriesTransientServerFailure() {
        enqueue(503, "{\"error\":{\"message\":\"overloaded\"}}", null, null);
        enqueue(200, completion("recovered"), null, null);

        String result = gateway.generate(AiPrompt.text("system", "question", 100));

        assertThat(result).isEqualTo("recovered");
        assertThat(requestBodies).hasSize(2);
    }

    @Test
    void retriesOnceWithMinimalPayloadWhenArgumentsAreUnsupported() {
        enqueue(400, "{\"error\":{\"code\":\"invalid_request_error\",\"message\":\"unsupported parameter: max_tokens\"}}",
                null, null);
        enqueue(200, completion("ok"), null, null);

        String result = gateway.generate(AiPrompt.json("system", "question", 300));

        assertThat(result).isEqualTo("ok");
        assertThat(requestBodies).hasSize(2);
        JsonNode minimal = requestBodies.get(1);
        assertThat(minimal.path("model").asText()).isEqualTo("auto");
        assertThat(minimal.path("stream").asBoolean()).isFalse();
        assertThat(minimal.has("messages")).isTrue();
        assertThat(minimal.has("temperature")).isTrue();
        assertThat(minimal.has("max_tokens")).isFalse();
        assertThat(minimal.has("response_format")).isFalse();
        assertThat(minimal.has("reasoning_effort")).isFalse();
    }

    @Test
    void quotaExhaustionFailsImmediately() {
        enqueue(429, "{\"error\":{\"code\":\"quota_exceeded\",\"message\":\"no quota\"}}", null, null);

        assertThatThrownBy(() -> gateway.generate(AiPrompt.text("system", "question", 100)))
                .isInstanceOf(AiRateLimitException.class);

        assertThat(requestBodies).hasSize(1);
    }

    @Test
    void rejectedKeyFailsImmediatelyAndNamesTheConfigurationVariable() {
        enqueue(401, "{\"error\":{\"message\":\"bad key\"}}", null, null);

        assertThatThrownBy(() -> gateway.generate(AiPrompt.text("system", "question", 100)))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("FREELLMAPI_API_KEY");

        assertThat(requestBodies).hasSize(1);
    }

    private void enqueue(int status, String body, String headerName, String headerValue) {
        replies.add(new Reply(status, body, headerName, headerValue));
    }

    /** Builds an OpenAI completion, using the array-of-parts form the router may return. */
    private String completion(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                    "message", Map.of("content", List.of(Map.of("type", "text", "text",
                            "   \n" + content))),
                    "finish_reason", "stop"))));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Reply(int status, String body, String headerName, String headerValue) {
    }
}
