package com.hess.thevault.auth;

import com.hess.thevault.auth.dto.AuthResponse;
import com.hess.thevault.auth.dto.HashPasswordRequest;
import com.hess.thevault.auth.dto.HashPasswordResponse;
import com.hess.thevault.auth.dto.LoginRequest;
import com.hess.thevault.auth.dto.MeResponse;
import com.hess.thevault.auth.dto.RefreshTokenRequest;
import com.hess.thevault.auth.dto.RefreshTokenResponse;
import com.hess.thevault.auth.dto.ValidateRegistrationRequest;
import com.hess.thevault.auth.dto.ValidationResponse;
import com.hess.thevault.audit.Audited;
import com.vault.sdk.VaultUser;
import com.vault.sdk.VaultUserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final VaultUserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(
            PasswordEncoder passwordEncoder,
            VaultUserRepository userRepository,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public ValidationResponse validateRegistration(ValidateRegistrationRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.findByEmail(email).isPresent()) {
            return ValidationResponse.failure("Email is already registered");
        }

        return ValidationResponse.success();
    }

    public HashPasswordResponse hashPassword(HashPasswordRequest request) {
        return new HashPasswordResponse(passwordEncoder.encode(request.password()));
    }

    @Audited(action = "LOGIN", resource = "/auth/login")
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        VaultUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        return toAuthResponse(user);
    }

    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken().trim();

        if (!jwtService.isTokenValid(refreshToken, JwtService.TOKEN_TYPE_REFRESH)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        VaultUser user = userRepository.findById(jwtService.extractVaultId(refreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        return new RefreshTokenResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.getAccessTokenExpirationMs()
        );
    }

    public void logout(String authorizationHeader) {
        String accessToken = resolveBearerToken(authorizationHeader);

        if (!jwtService.isTokenValid(accessToken, JwtService.TOKEN_TYPE_ACCESS)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication token");
        }

        Duration ttl = jwtService.getRemainingValidity(accessToken);

        try {
            tokenBlacklistService.blacklist(accessToken, ttl);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to logout safely");
        }
    }

    public MeResponse me(String authorizationHeader) {
        String token = resolveBearerToken(authorizationHeader);

        if (!jwtService.isTokenValid(token, JwtService.TOKEN_TYPE_ACCESS)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication token");
        }

        return new MeResponse(
                jwtService.extractVaultId(token),
                jwtService.extractEmail(token),
                jwtService.extractTenantId(token),
                jwtService.extractRole(token)
        );
    }

    private AuthResponse toAuthResponse(VaultUser user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.getAccessTokenExpirationMs(),
                user.getVaultId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication token");
        }

        String token = authorizationHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication token");
        }

        return token;
    }
}
