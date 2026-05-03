package com.hess.thevault.apikey.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String name,
        String tenantId,
        String createdBy,
        List<String> scopes,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        boolean revoked,
        LocalDateTime createdAt
) {
}
