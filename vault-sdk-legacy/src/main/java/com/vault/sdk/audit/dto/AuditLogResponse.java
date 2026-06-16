package com.vault.sdk.audit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String tenantId,
        String userId,
        String action,
        String resource,
        String ipAddress,
        String status,
        String metadata,
        LocalDateTime occurredAt
) {
}
