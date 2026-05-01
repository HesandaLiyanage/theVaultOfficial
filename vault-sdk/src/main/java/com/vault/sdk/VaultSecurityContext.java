package com.vault.sdk;

import java.util.UUID;

public class VaultSecurityContext {

    private static final ThreadLocal<VaultUser> CURRENT_USER = new ThreadLocal<>();

    public void setCurrentUser(VaultUser user) {
        CURRENT_USER.set(user);
    }

    public VaultUser getCurrentUser() {
        return CURRENT_USER.get();
    }

    public void clear() {
        CURRENT_USER.remove();
    }

    public record VaultUser(UUID userId, UUID tenantId, String email, String token) {
    }
}
