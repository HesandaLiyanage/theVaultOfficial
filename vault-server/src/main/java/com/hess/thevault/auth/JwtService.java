package com.hess.thevault.auth;

import com.hess.thevault.auth.VaultUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private static final String CLAIM_VAULT_ID = "vaultId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.signingKey = buildSigningKey(jwtSecret);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public String generateAccessToken(VaultUser user) {
        return generateToken(user, TOKEN_TYPE_ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(VaultUser user) {
        return generateToken(user, TOKEN_TYPE_REFRESH, refreshTokenExpirationMs);
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                return false;
            }

            Claims claims = extractAllClaims(token);
            requireClaim(claims, CLAIM_VAULT_ID);
            requireClaim(claims, CLAIM_EMAIL);
            requireClaim(claims, CLAIM_TENANT_ID);
            requireClaim(claims, CLAIM_ROLE);
            isRecognizedTokenType(requireClaim(claims, CLAIM_TYPE));
            return true;
        } catch (JwtException | IllegalArgumentException | DataAccessException ex) {
            return false;
        }
    }

    public boolean isTokenValid(String token, String expectedType) {
        return isTokenValid(token) && extractType(token).equals(expectedType);
    }

    public String extractVaultId(String token) {
        return requiredClaim(token, CLAIM_VAULT_ID);
    }

    public String extractUserId(String token) {
        return extractVaultId(token);
    }

    public String extractTenantId(String token) {
        return requiredClaim(token, CLAIM_TENANT_ID);
    }

    public String extractRole(String token) {
        return requiredClaim(token, CLAIM_ROLE);
    }

    public String extractEmail(String token) {
        return requiredClaim(token, CLAIM_EMAIL);
    }

    public String extractType(String token) {
        return requiredClaim(token, CLAIM_TYPE);
    }

    public Duration getRemainingValidity(String token) {
        Date expiresAt = extractAllClaims(token).getExpiration();
        if (expiresAt == null) {
            throw new JwtException("Missing JWT expiration");
        }

        return Duration.between(Instant.now(), expiresAt.toInstant());
    }

    private String generateToken(VaultUser user, String tokenType, long expirationMs) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .claims(Map.of(
                        CLAIM_VAULT_ID, user.getVaultId(),
                        CLAIM_EMAIL, user.getEmail(),
                        CLAIM_TENANT_ID, user.getTenantId().toString(),
                        CLAIM_ROLE, user.getRole(),
                        CLAIM_TYPE, tokenType
                ))
                .subject(user.getVaultId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private String requiredClaim(String token, String claimName) {
        return requireClaim(extractAllClaims(token), claimName);
    }

    private String requireClaim(Claims claims, String claimName) {
        Object claimValue = claims.get(claimName);
        if (!(claimValue instanceof String value) || value.isBlank()) {
            throw new JwtException("Missing required JWT claim: " + claimName);
        }
        return value;
    }

    private void isRecognizedTokenType(String tokenType) {
        if (!TOKEN_TYPE_ACCESS.equals(tokenType) && !TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new JwtException("Unsupported JWT type");
        }
    }

    private SecretKey buildSigningKey(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits for HS256");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
