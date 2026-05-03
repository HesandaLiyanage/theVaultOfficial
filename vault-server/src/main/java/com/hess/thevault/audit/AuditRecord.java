package com.hess.thevault.audit;

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
