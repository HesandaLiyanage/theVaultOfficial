package com.hess.thevault.auth;

import com.vault.sdk.VaultUser;
import com.vault.sdk.VaultUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@Profile("stub")
public class StubVaultUserRepository implements VaultUserRepository {

    private static final String TEST_USER_ID = "stub-user-1";
    private static final String TEST_EMAIL = "test@test.com";
    private static final String ADMIN_USER_ID = "stub-admin-1";
    private static final String ADMIN_EMAIL = "admin@test.com";

    private final Map<String, VaultUser> usersByEmail;
    private final Map<String, VaultUser> usersById;

    public StubVaultUserRepository(PasswordEncoder passwordEncoder) {
        VaultUser testUser = new StubVaultUser(
                TEST_USER_ID,
                TEST_EMAIL,
                passwordEncoder.encode("password"),
                "USER",
                "stub-tenant-1"
        );
        VaultUser adminUser = new StubVaultUser(
                ADMIN_USER_ID,
                ADMIN_EMAIL,
                passwordEncoder.encode("password"),
                "TENANT_ADMIN",
                "stub-tenant-1"
        );
        this.usersByEmail = Map.of(TEST_EMAIL, testUser, ADMIN_EMAIL, adminUser);
        this.usersById = Map.of(TEST_USER_ID, testUser, ADMIN_USER_ID, adminUser);
    }

    @Override
    public Optional<VaultUser> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email.toLowerCase()));
    }

    @Override
    public Optional<VaultUser> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
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
