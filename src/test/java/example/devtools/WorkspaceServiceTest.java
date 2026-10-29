package example.devtools;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceTest {
    @Test
    void joinsOnlyMatchingCompanyEmailAfterDomainProof() {
        List<String> calls = new ArrayList<>();
        WorkspaceService.Gateway gateway = (method, path, body, key) -> {
            calls.add(method + " " + path);
            if (path.endsWith("/add")) return Map.of("zone_id", "zone-42");
            if (path.endsWith("/upsert")) assertEquals("zone-42", body.get("zone_id"));
            if (path.endsWith("/create")) {
                assertEquals("lesson-tools.example", ((Map<?, ?>) body.get("metadata")).get("workspace_domain"));
                assertEquals("release-7", body.get("idempotency_key"));
            }
            return Map.of();
        };
        WorkspaceService service = new WorkspaceService(gateway, "_workspace-verification", "proof-123");
        var wrong = new WorkspaceService.JoinRequest("lee@other.example", "lesson-tools.example",
                "release-7", "build-passed", "publish", "compiler-clean");
        assertThrows(IllegalArgumentException.class, () -> service.join(wrong));
        assertTrue(calls.isEmpty());
        var right = new WorkspaceService.JoinRequest("lee@lesson-tools.example", "lesson-tools.example",
                "release-7", "build-passed", "publish", "compiler-clean");
        assertEquals("joined", service.join(right).get("status"));
        assertEquals(List.of("POST /v1/dns/domain/add", "PUT /v1/dns/record/upsert",
                "POST /v1/dns/domain/verify", "POST /v1/auth/user/create"), calls);
    }
}
