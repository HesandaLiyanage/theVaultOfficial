package com.hess.thevault.auth;

public interface VaultUser {

    String getVaultId();

    String getEmail();

    String getPasswordHash();

    String getRole();

    String getTenantId();
}
