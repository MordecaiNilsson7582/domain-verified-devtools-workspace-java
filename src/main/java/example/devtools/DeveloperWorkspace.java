package example.devtools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@SpringBootApplication
public class DeveloperWorkspace {
    public static void main(String[] args) { SpringApplication.run(DeveloperWorkspace.class, args); }

    @Bean
    WorkspaceService workspaceService(@Value("${infrai.base-url}") String baseUrl,
            @Value("${workspace.proof-name}") String proofName,
            @Value("${workspace.proof-content}") String proofContent) {
        return new WorkspaceService(new InfraiGateway(baseUrl), proofName, proofContent);
    }

    @RestController
    static class JoinController {
        private final WorkspaceService service;
        JoinController(WorkspaceService service) { this.service = service; }

        @PostMapping("/workspace/join")
        Map<String, Object> join(@RequestBody WorkspaceService.JoinRequest request) {
            return service.join(request);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }

        @ExceptionHandler(InfraiGateway.ApiRejection.class)
        ResponseEntity<Map<String, String>> rejected(InfraiGateway.ApiRejection error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            return ResponseEntity.status(status).body(Map.of("error", error.getMessage()));
        }
    }
}
