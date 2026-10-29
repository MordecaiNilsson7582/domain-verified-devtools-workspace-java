package example.devtools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class InfraiGateway implements WorkspaceService.Gateway {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String key;

    InfraiGateway(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
    }

    @Override
    public Map<String, Object> call(String method, String path, Map<String, Object> body, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", idempotencyKey)
                        .timeout(Duration.ofSeconds(20))
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After").map(value -> {
                        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
                    }).orElse(0L);
                    Thread.sleep(Math.max(seconds * 1000, 250L << attempt));
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    throw new ApiRejection(response.statusCode(), envelope.path("error").toString());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Remote service error");
                JsonNode data = envelope.path("data");
                return data.isObject() ? json.convertValue(data, Map.class) : Map.of();
            } catch (IOException e) {
                throw new IllegalStateException("Could not exchange the request", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    static final class ApiRejection extends RuntimeException {
        private final int status;
        ApiRejection(int status, String detail) { super(detail); this.status = status; }
        int status() { return status; }
    }
}
