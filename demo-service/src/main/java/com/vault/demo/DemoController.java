package com.vault.demo;

import io.github.hesandaliyanage.vault.sdk.VaultPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample endpoints showing how a consumer app uses vault-sdk:
 * <ul>
 *   <li>{@code /public/ping} is in {@code vault.filter.public-paths}, so the
 *       SDK filter lets it through without a token.</li>
 *   <li>{@code /protected/*} requires any valid JWT or API key.</li>
 *   <li>{@code /admin/*} additionally requires {@code ROLE_ADMIN}, demonstrating
 *       that {@code @PreAuthorize} works against the role and scope claims
 *       returned by vault-server.</li>
 * </ul>
 */
@RestController
public class DemoController {

    @GetMapping("/public/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/protected/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal VaultPrincipal principal) {
        return Map.of(
                "userId", principal.userId(),
                "tenantId", principal.tenantId(),
                "role", principal.role(),
                "scopes", principal.scopes()
        );
    }

    @GetMapping("/protected/data")
    @PreAuthorize("hasAuthority('orders:read')")
    public List<Map<String, Object>> readData() {
        return List.of(
                Map.of("id", 1, "name", "Northwind report"),
                Map.of("id", 2, "name", "Usage summary")
        );
    }

    @PostMapping("/protected/data")
    @PreAuthorize("hasAuthority('orders:write')")
    public Map<String, Object> writeData() {
        return Map.of("created", true);
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminStats() {
        return Map.of(
                "totalRequests", 1042,
                "activeUsers", 17
        );
    }
}
