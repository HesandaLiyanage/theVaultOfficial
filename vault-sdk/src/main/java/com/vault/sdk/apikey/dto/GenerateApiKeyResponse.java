package com.vault.sdk.apikey.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GenerateApiKeyResponse(
        UUID id,
        String apiKey,
        String keyPrefix,
        String name,
        String tenantId,
        List<String> scopes,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
