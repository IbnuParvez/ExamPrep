package com.examprep.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import com.google.genai.errors.ApiException;

/** The retry decision must come from the provider's status code, not from message text. */
class GenAiGeminiGatewayTest {

    @Test
    void onlyTransientStatusCodesAreRetryable() {
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(429, "RESOURCE_EXHAUSTED")).isTrue();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(408, null)).isTrue();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(500, null)).isTrue();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(503, "UNAVAILABLE")).isTrue();

        assertThat(GenAiGeminiGateway.isRetryableApiFailure(400, "INVALID_ARGUMENT")).isFalse();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(401, "UNAUTHENTICATED")).isFalse();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(403, "PERMISSION_DENIED")).isFalse();
        assertThat(GenAiGeminiGateway.isRetryableApiFailure(404, "NOT_FOUND")).isFalse();
    }

    @Test
    void typedApiExceptionsAndIoFailuresDriveTheDecision() {
        assertThat(GenAiGeminiGateway.isRetryable(
                new ApiException(429, "RESOURCE_EXHAUSTED", "slow down"))).isTrue();
        assertThat(GenAiGeminiGateway.isRetryable(
                new ApiException(400, "INVALID_ARGUMENT", "token count 500 is not a status"))).isFalse();
        assertThat(GenAiGeminiGateway.isRetryable(
                new RuntimeException(new SocketTimeoutException("read timed out")))).isTrue();
        assertThat(GenAiGeminiGateway.isRetryable(new IllegalStateException("no retry for this"))).isFalse();
    }

    @Test
    void backoffGrowsPerAttemptAndStaysBounded() {
        assertThat(GenAiGeminiGateway.backoffMillis(1, 5_000)).isBetween(5_000L, 6_000L);
        assertThat(GenAiGeminiGateway.backoffMillis(2, 5_000)).isBetween(10_000L, 11_000L);
        assertThat(GenAiGeminiGateway.backoffMillis(3, 5_000)).isBetween(20_000L, 21_000L);
        assertThat(GenAiGeminiGateway.backoffMillis(12, 5_000)).isBetween(60_000L, 61_000L);
    }
}
