package dev.infrai.logistics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class InfraiErrorsClient implements ShipmentFailureService.ErrorReporter {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient http;
    private final ServiceConfig config;

    public InfraiErrorsClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** infrai.errors.capture maps to this explicit POST request. */
    @Override
    public void capture(Map<String, Object> exceptionPayload) {
        String requestBody = Json.stringify(exceptionPayload);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/v1/errors/capture"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = send(request);
            Envelope envelope = Envelope.decode(response.body(), response.statusCode());

            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiApiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiTransportException("Infrai request returned HTTP " + response.statusCode());
            }
            return;
        }
        throw new InfraiTransportException("Infrai request retry budget exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new InfraiTransportException("Infrai transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InfraiTransportException("Infrai request interrupted", exception);
        }
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiErrorsClient::parseRetryAfterMillis)
                .orElse(250L * (1L << attempt));
    }

    private static long parseRetryAfterMillis(String value) {
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1_000L);
        } catch (NumberFormatException ignored) {
            return 1_000L;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InfraiTransportException("Retry interrupted", exception);
        }
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage) {
        @SuppressWarnings("unchecked")
        static Envelope decode(String body, int statusCode) {
            final Object decoded;
            try {
                decoded = Json.parse(body);
            } catch (IllegalArgumentException exception) {
                throw new InfraiTransportException("Invalid response envelope at HTTP " + statusCode, exception);
            }
            if (!(decoded instanceof Map<?, ?> envelope)) {
                throw new InfraiTransportException("Response envelope must be an object");
            }
            boolean ok = Boolean.TRUE.equals(envelope.get("ok"));
            Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            return new Envelope(ok, text(error.get("code"), "UNKNOWN"),
                    text(error.get("message"), "Request rejected"));
        }

        private static String text(Object value, String fallback) {
            return value instanceof String text && !text.isBlank() ? text : fallback;
        }
    }

    public static final class InfraiApiException extends RuntimeException {
        private final String code;
        private final int statusCode;

        public InfraiApiException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }

    public static final class InfraiTransportException extends RuntimeException {
        public InfraiTransportException(String message) { super(message); }
        public InfraiTransportException(String message, Throwable cause) { super(message, cause); }
    }
}
