package com.vault.sdk.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vault_audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 200)
    private String resource;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected AuditLog() {
    }

    public AuditLog(
            String tenantId,
            String userId,
            String action,
            String resource,
            String ipAddress,
            String status,
            String metadata
    ) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.action = action;
        this.resource = resource;
        this.ipAddress = ipAddress;
        this.status = status;
        this.metadata = metadata;
        this.occurredAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getResource() {
        return resource;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getStatus() {
        return status;
    }

    public String getMetadata() {
        return metadata;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
