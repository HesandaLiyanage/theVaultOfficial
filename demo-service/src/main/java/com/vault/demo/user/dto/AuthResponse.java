package com.vault.demo.user.dto;

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
}
