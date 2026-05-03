package com.vault.demo.user;

import com.vault.sdk.VaultUser;
import com.vault.sdk.VaultUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserVaultAdapter implements VaultUserRepository {

    private final UserRepository userRepository;

    public UserVaultAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<VaultUser> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> (VaultUser) user);
    }

    @Override
    public Optional<VaultUser> findById(String id) {
        return userRepository.findById(UUID.fromString(id))
                .map(user -> (VaultUser) user);
    }
}
