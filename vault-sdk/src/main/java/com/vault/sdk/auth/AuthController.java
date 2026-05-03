package com.vault.sdk.auth;

import com.vault.sdk.auth.dto.AuthResponse;
import com.vault.sdk.auth.dto.HashPasswordRequest;
import com.vault.sdk.auth.dto.HashPasswordResponse;
import com.vault.sdk.auth.dto.LoginRequest;
import com.vault.sdk.auth.dto.MeResponse;
import com.vault.sdk.auth.dto.RefreshTokenRequest;
import com.vault.sdk.auth.dto.RefreshTokenResponse;
import com.vault.sdk.auth.dto.ValidateRegistrationRequest;
import com.vault.sdk.auth.dto.ValidationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/validate-registration")
    public ValidationResponse validateRegistration(@Valid @RequestBody ValidateRegistrationRequest request) {
        return authService.validateRegistration(request);
    }

    @PostMapping("/hash-password")
    public HashPasswordResponse hashPassword(@Valid @RequestBody HashPasswordRequest request) {
        return authService.hashPassword(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public RefreshTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        return authService.me(authorizationHeader);
    }
}
