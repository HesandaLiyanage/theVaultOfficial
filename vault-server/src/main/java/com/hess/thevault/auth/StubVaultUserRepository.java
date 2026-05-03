package com.hess.thevault.auth;

import com.vault.sdk.VaultUser;
import com.vault.sdk.VaultUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("stub")
public class StubVaultUserRepository implements VaultUserRepository {

    private static final String TEST_USER_ID = "stub-user-1";
    private static final String TEST_EMAIL = "test@test.com";

    private final VaultUser testUser;

    public StubVaultUserRepository(PasswordEncoder passwordEncoder) {
        this.testUser = new StubVaultUser(
                TEST_USER_ID,
                TEST_EMAIL,
                passwordEncoder.encode("password12345"),
                "USER",
                "stub-tenant-1"
        );
    }

    @Override
    public Optional<VaultUser> findByEmail(String email) {
        if (TEST_EMAIL.equalsIgnoreCase(email)) {
            return Optional.of(testUser);
        }
        return Optional.empty();
    }

    @Override
    public Optional<VaultUser> findById(String id) {
        if (TEST_USER_ID.equals(id)) {
            return Optional.of(testUser);
        }
        return Optional.empty();
    }

    private record StubVaultUser(
            String vaultId,
            String email,
            String passwordHash,
            String role,
            String tenantId
    ) implements VaultUser {

        @Override
        public String getVaultId() {
            return vaultId;
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
            return tenantId;
        }
    }
}
