package com.hess.thevault.auth;

import com.hess.thevault.user.Role;
import com.hess.thevault.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.signingKey = buildSigningKey(jwtSecret);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TOKEN_TYPE_ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TOKEN_TYPE_REFRESH, refreshTokenExpirationMs);
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
            Claims claims = extractAllClaims(token);
            requireClaim(claims, CLAIM_USER_ID);
            requireClaim(claims, CLAIM_TENANT_ID);
            Role.valueOf(requireClaim(claims, CLAIM_ROLE));
            isRecognizedTokenType(requireClaim(claims, CLAIM_TYPE));
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isTokenValid(String token, String expectedType) {
        return isTokenValid(token) && extractType(token).equals(expectedType);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(requiredClaim(token, CLAIM_USER_ID));
    }

    public UUID extractTenantId(String token) {
        return UUID.fromString(requiredClaim(token, CLAIM_TENANT_ID));
    }

    public Role extractRole(String token) {
        return Role.valueOf(requiredClaim(token, CLAIM_ROLE));
    }

    public String extractType(String token) {
        return requiredClaim(token, CLAIM_TYPE);
    }

    private String generateToken(User user, String tokenType, long expirationMs) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId().toString(),
                        CLAIM_TENANT_ID, user.getTenantId().toString(),
                        CLAIM_ROLE, user.getRole().name(),
                        CLAIM_TYPE, tokenType
                ))
                .subject(user.getUsername())
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
