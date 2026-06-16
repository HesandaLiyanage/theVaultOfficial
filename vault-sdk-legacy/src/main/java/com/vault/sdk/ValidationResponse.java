package com.vault.sdk;

import java.util.List;

@Deprecated(since = "0.2.0", forRemoval = true)
public record ValidationResponse(
        boolean valid,
        String userId,
        String tenantId,
        String role,
        List<String> scopes,
        String reason,
        String apiKeyId,
        String authSource
) {

    public List<String> scopes() {
        return scopes == null ? List.of() : scopes;
    }
}
