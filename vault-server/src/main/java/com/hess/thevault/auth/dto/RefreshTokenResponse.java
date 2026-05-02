package com.hess.thevault.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInMs
) {
    public RefreshTokenResponse {
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
    }
}
