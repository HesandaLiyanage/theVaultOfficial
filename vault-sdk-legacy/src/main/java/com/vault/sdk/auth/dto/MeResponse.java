package com.vault.sdk.auth.dto;

public record MeResponse(
        String userId,
        String email,
        String tenantId,
        String role
) {
}
