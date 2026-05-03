package com.vault.sdk;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

public class VaultClient {

    private final VaultProperties properties;
    private final RestClient restClient;

    public VaultClient(VaultProperties properties) {
        if (!StringUtils.hasText(properties.getServiceApiKey())) {
            throw new IllegalStateException("Vault SDK requires vault.service-api-key to call vault-server internal endpoints");
        }

        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Service-Key", properties.getServiceApiKey() == null ? "" : properties.getServiceApiKey())
                .build();
    }

    public ValidationResponse validate(String token) {
        return restClient.post()
                .uri("/internal/validate")
                .body(Map.of(
                        "token", token,
                        "type", detectType(token)
                ))
                .retrieve()
                .body(ValidationResponse.class);
    }

    public void auditAsync(HttpServletRequest request, ValidationResponse validation, int status) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        Thread.startVirtualThread(() -> {
            try {
                audit(method, path, validation, status);
            } catch (RuntimeException ignored) {
                // Audit must never fail the protected application request.
            }
        });
    }

    private void audit(String method, String path, ValidationResponse validation, int status) {
        Map<String, String> body = new HashMap<>();
        body.put("tenantId", nullToEmpty(validation.tenantId()));
        body.put("userId", nullToEmpty(validation.userId()));
        body.put("action", method);
        body.put("resource", path);
        body.put("status", status >= 400 ? "FAILURE" : "SUCCESS");

        restClient.post()
                .uri("/internal/audit")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String detectType(String token) {
        return token != null && token.startsWith("vault_") ? "API_KEY" : "JWT";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public VaultProperties getProperties() {
        return properties;
    }
}
