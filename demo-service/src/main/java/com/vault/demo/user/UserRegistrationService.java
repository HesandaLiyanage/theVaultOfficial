package com.vault.demo.user;

import com.vault.demo.user.dto.AuthResponse;
import com.vault.demo.user.dto.HashPasswordResponse;
import com.vault.demo.user.dto.RegisterUserRequest;
import com.vault.demo.user.dto.RegistrationResponse;
import com.vault.demo.user.dto.VaultValidationResponse;
import com.vault.sdk.VaultProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Map;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RestClient vaultClient;

    public UserRegistrationService(UserRepository userRepository, VaultProperties properties) {
        this.userRepository = userRepository;
        this.vaultClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Transactional
    public RegistrationResponse register(RegisterUserRequest request) {
        String email = normalizeEmail(request.email());
        validateWithVault(email, request.password());

        HashPasswordResponse hashResponse = vaultClient.post()
                .uri("/auth/hash-password")
                .body(Map.of("password", request.password()))
                .retrieve()
                .body(HashPasswordResponse.class);

        if (hashResponse == null || hashResponse.hash() == null || hashResponse.hash().isBlank()) {
            throw new RegistrationException("Vault did not return a password hash");
        }

        User saved = userRepository.save(new User(
                email,
                hashResponse.hash(),
                request.firstName().trim(),
                request.companyName().trim(),
                request.tenantId()
        ));

        AuthResponse authResponse = vaultClient.post()
                .uri("/auth/login")
                .body(Map.of("email", email, "password", request.password()))
                .retrieve()
                .body(AuthResponse.class);

        if (authResponse == null) {
            throw new RegistrationException("Vault did not return authentication tokens");
        }

        return new RegistrationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getFirstName(),
                saved.getCompanyName(),
                saved.getSubscriptionTier(),
                saved.getRole(),
                saved.getTenantId(),
                authResponse.accessToken(),
                authResponse.refreshToken(),
                authResponse.tokenType(),
                authResponse.expiresInMs()
        );
    }

    private void validateWithVault(String email, String password) {
        VaultValidationResponse validation = vaultClient.post()
                .uri("/auth/validate-registration")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .body(VaultValidationResponse.class);

        if (validation == null || !validation.valid()) {
            String reason = validation == null || validation.reason() == null
                    ? "Registration validation failed"
                    : validation.reason();
            throw new RegistrationException(reason);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
