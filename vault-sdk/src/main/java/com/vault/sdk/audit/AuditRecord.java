package com.vault.sdk.audit;

public record AuditRecord(
        String tenantId,
        String userId,
        String action,
        String resource,
        String ipAddress,
        String status,
        String metadata
) {
}
