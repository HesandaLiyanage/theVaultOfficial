package com.vault.sdk;

@Deprecated(since = "0.2.0", forRemoval = true)
public interface VaultUser {

    String getVaultId();

    String getEmail();

    String getPasswordHash();

    String getRole();

    String getTenantId();
}
