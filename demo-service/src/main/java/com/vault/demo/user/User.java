package com.vault.demo.user;

import com.vault.sdk.VaultUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User implements VaultUser {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "subscription_tier", nullable = false, length = 50)
    private String subscriptionTier;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(
            String email,
            String passwordHash,
            String firstName,
            String companyName,
            UUID tenantId
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.companyName = companyName;
        this.tenantId = tenantId;
        this.subscriptionTier = "FREE";
        this.role = "USER";
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String getVaultId() {
        return id.toString();
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getPasswordHash() {
        return passwordHash;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getTenantId() {
        return tenantId.toString();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getSubscriptionTier() {
        return subscriptionTier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
