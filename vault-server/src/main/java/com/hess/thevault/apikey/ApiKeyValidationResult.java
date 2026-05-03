package com.hess.thevault.apikey;

import java.util.List;

public record ApiKeyValidationResult(
        boolean valid,
        String reason,
        String keyId,
        String tenantId,
        String createdBy,
        List<String> scopes
) {

    public static ApiKeyValidationResult success(ApiKey apiKey, List<String> scopes) {
        return new ApiKeyValidationResult(
                true,
                null,
                apiKey.getId().toString(),
                apiKey.getTenantId(),
                apiKey.getCreatedBy(),
                scopes
        );
    }

    public static ApiKeyValidationResult failure(String reason) {
        return new ApiKeyValidationResult(false, reason, null, null, null, List.of());
    }
}
