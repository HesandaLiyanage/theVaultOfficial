package com.hess.thevault.auth.dto;

import com.hess.thevault.user.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        UUID tenantId,
        Role role,
        boolean enabled,
        LocalDateTime createdAt
) {
}
