package example.devtools;

import java.util.Locale;
import java.util.Map;

final class WorkspaceService {
    interface Gateway {
        Map<String, Object> call(String method, String path, Map<String, Object> body, String idempotencyKey);
    }

    private final Gateway gateway;
    private final String proofName;
    private final String proofContent;

    WorkspaceService(Gateway gateway, String proofName, String proofContent) {
        this.gateway = gateway;
        this.proofName = proofName;
        this.proofContent = proofContent;
    }

    Map<String, Object> join(JoinRequest request) {
        if (request.domain() == null || request.email() == null || request.buildEvent() == null
                || request.releaseOperation() == null || request.diagnostic() == null) {
            throw new IllegalArgumentException("All join fields are required");
        }
        String domain = request.domain().toLowerCase(Locale.ROOT).strip();
        String email = request.email().toLowerCase(Locale.ROOT).strip();
        if (domain.isEmpty() || !email.endsWith("@" + domain) || proofContent.isBlank()
                || request.eventId() == null || request.eventId().isBlank()) {
            throw new IllegalArgumentException("Company email, domain, proof content and event ID are required");
        }
        Map<String, Object> zone = gateway.call("POST", "/v1/dns/domain/add", Map.of("domain", domain), request.eventId());
        Object zoneId = zone.get("zone_id");
        if (zoneId == null) throw new IllegalStateException("Domain response has no zone_id");
        gateway.call("PUT", "/v1/dns/record/upsert", Map.of(
                "zone_id", zoneId, "record_type", "TXT", "name", proofName, "content", proofContent), request.eventId());
        gateway.call("POST", "/v1/dns/domain/verify", Map.of("domain", domain), request.eventId());
        Map<String, Object> metadata = Map.of("workspace_domain", domain,
                "build_event", request.buildEvent(), "release_operation", request.releaseOperation(),
                "diagnostic", request.diagnostic());
        gateway.call("POST", "/v1/auth/user/create", Map.of("email", email,
                "metadata", metadata, "idempotency_key", request.eventId()), request.eventId());
        return Map.of("email", email, "workspace_domain", domain, "status", "joined",
                "build_event", request.buildEvent(), "release_operation", request.releaseOperation(),
                "diagnostic", request.diagnostic());
    }

    record JoinRequest(String email, String domain, String eventId, String buildEvent,
                       String releaseOperation, String diagnostic) {}
}
