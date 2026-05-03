package com.vault.sdk.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vault_api_keys")
public class ApiKey {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 10)
    private String keyPrefix;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private String scopes;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ApiKey() {
    }

    public ApiKey(String keyHash, String keyPrefix, String name, String tenantId, String createdBy, String scopes, LocalDateTime expiresAt) {
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.name = name;
        this.tenantId = tenantId;
        this.createdBy = createdBy;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getName() {
        return name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getScopes() {
        return scopes;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void markUsed(LocalDateTime usedAt) {
        this.lastUsedAt = usedAt;
    }

    public void revoke() {
        this.revoked = true;
    }
}
