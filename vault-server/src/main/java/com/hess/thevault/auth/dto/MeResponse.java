package com.hess.thevault.auth.dto;

public record MeResponse(
        String userId,
        String email,
        String tenantId,
        String role
) {
}
