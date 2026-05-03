package com.vault.sdk.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @Transactional
    public void record(AuditRecord record) {
        auditLogRepository.save(new AuditLog(
                record.tenantId(),
                record.userId(),
                record.action(),
                record.resource(),
                record.ipAddress(),
                record.status(),
                record.metadata()
        ));
    }
}
