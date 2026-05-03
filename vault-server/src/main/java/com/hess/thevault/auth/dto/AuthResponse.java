package com.hess.thevault.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        String userId,
        String tenantId,
        String email,
        String role
) {
    public AuthResponse {
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
    }
}
