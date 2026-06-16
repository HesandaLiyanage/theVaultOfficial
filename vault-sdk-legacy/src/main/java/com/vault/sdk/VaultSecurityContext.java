package com.vault.sdk;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

@Deprecated(since = "0.2.0", forRemoval = true)
public class VaultSecurityContext {

    public void setAuthentication(VaultAuthentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public VaultAuthentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof VaultAuthentication vaultAuthentication) {
            return vaultAuthentication;
        }
        return null;
    }

    public String getUserId() {
        VaultAuthentication authentication = getAuthentication();
        return authentication == null ? null : authentication.getUserId();
    }

    public String getTenantId() {
        VaultAuthentication authentication = getAuthentication();
        return authentication == null ? null : authentication.getTenantId();
    }

    public VaultUser getCurrentUser() {
        VaultAuthentication authentication = getAuthentication();
        if (authentication == null) {
            return null;
        }
        return new VaultUser(
                toUuid(authentication.getUserId()),
                toUuid(authentication.getTenantId()),
                null,
                null
        );
    }

    public void clear() {
        SecurityContextHolder.clearContext();
    }

    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record VaultUser(UUID userId, UUID tenantId, String email, String token) {
    }
}
