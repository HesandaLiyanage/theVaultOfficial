package com.vault.sdk;

public interface VaultUser {

    String getVaultId();

    String getEmail();

    String getPasswordHash();

    String getRole();

    String getTenantId();
}
