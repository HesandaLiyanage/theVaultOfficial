package com.vault.sdk.audit;

import com.vault.sdk.VaultAuthentication;
import com.vault.sdk.audit.dto.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vault/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','TENANT_ADMIN')")
    public List<AuditLogResponse> logs(Authentication authentication) {
        VaultAuthentication current = (VaultAuthentication) authentication;
        return auditLogRepository.findByTenantIdOrderByOccurredAtDesc(current.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getTenantId(),
                log.getUserId(),
                log.getAction(),
                log.getResource(),
                log.getIpAddress(),
                log.getStatus(),
                log.getMetadata(),
                log.getOccurredAt()
        );
    }
}
