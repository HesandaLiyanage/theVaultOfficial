package com.vault.demo.user.dto;

import java.util.UUID;

public record RegistrationResponse(
        UUID userId,
        String email,
        String firstName,
        String companyName,
        String subscriptionTier,
        String role,
        String tenantId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs
) {
}
