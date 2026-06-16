package com.hess.thevault.auth;

import java.util.Optional;

public interface VaultUserRepository {

    Optional<VaultUser> findByEmail(String email);

    Optional<VaultUser> findById(String id);
}
