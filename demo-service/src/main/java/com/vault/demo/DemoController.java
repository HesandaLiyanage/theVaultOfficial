package com.vault.demo;

import com.vault.demo.user.UserRegistrationService;
import com.vault.demo.user.dto.RegisterUserRequest;
import com.vault.demo.user.dto.RegistrationResponse;
import com.vault.sdk.VaultAuthentication;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class DemoController {

    private final UserRegistrationService userRegistrationService;

    public DemoController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/users/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userRegistrationService.register(request);
    }

    @GetMapping("/public/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/protected/profile")
    public Map<String, Object> profile(Authentication authentication) {
        VaultAuthentication vaultAuthentication = (VaultAuthentication) authentication;
        return Map.of(
                "userId", vaultAuthentication.getUserId(),
                "tenantId", vaultAuthentication.getTenantId(),
                "role", vaultAuthentication.getRole(),
                "message", "authenticated"
        );
    }

    @GetMapping("/protected/data")
    @PreAuthorize("hasAuthority('READ')")
    public List<Map<String, Object>> readData() {
        return List.of(
                Map.of("id", 1, "name", "Northwind report"),
                Map.of("id", 2, "name", "Usage summary"),
                Map.of("id", 3, "name", "Billing snapshot")
        );
    }

    @PostMapping("/protected/data")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
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
