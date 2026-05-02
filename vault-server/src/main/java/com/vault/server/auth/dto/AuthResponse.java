package com.vault.server.auth.dto;

import com.hess.thevault.user.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UUID userId,
        UUID tenantId,
        String email,
        Role role
) {
    public AuthResponse {
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
    }
}
