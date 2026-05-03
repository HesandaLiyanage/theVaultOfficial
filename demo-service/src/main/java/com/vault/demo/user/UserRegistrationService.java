package com.vault.demo.user;

import com.vault.demo.user.dto.RegisterUserRequest;
import com.vault.demo.user.dto.RegistrationResponse;
import com.vault.sdk.auth.AuthService;
import com.vault.sdk.auth.dto.AuthResponse;
import com.vault.sdk.auth.dto.HashPasswordRequest;
import com.vault.sdk.auth.dto.HashPasswordResponse;
import com.vault.sdk.auth.dto.LoginRequest;
import com.vault.sdk.auth.dto.ValidateRegistrationRequest;
import com.vault.sdk.auth.dto.ValidationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserRegistrationService(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional
    public RegistrationResponse register(RegisterUserRequest request) {
        String email = normalizeEmail(request.email());
        validateRegistration(email, request.password());

        HashPasswordResponse hashResponse = authService.hashPassword(new HashPasswordRequest(request.password()));

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

        AuthResponse authResponse = authService.login(new LoginRequest(email, request.password()));

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

    private void validateRegistration(String email, String password) {
        ValidationResponse validation = authService.validateRegistration(new ValidateRegistrationRequest(email, password));

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
