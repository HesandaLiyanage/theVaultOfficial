package com.vault.sdk;

import java.util.Optional;

@Deprecated(since = "0.2.0", forRemoval = true)
public interface VaultUserRepository {

    Optional<VaultUser> findByEmail(String email);

    Optional<VaultUser> findById(String id);
}
