package io.github.hesandaliyanage.vault.sdk.functional;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates RSA keypairs, signs JWTs, and produces JWKS bodies for the
 * functional test suite. One instance per test class is fine — its keypair
 * is the JWKS the {@link FakeVaultServer} will publish.
 */
public final class JwtTestSigner {

    private final KeyPair keyPair;
    private final String keyId;
    private final RSASSASigner signer;

    public JwtTestSigner() {
        this(UUID.randomUUID().toString());
    }

    public JwtTestSigner(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
        this.keyId = keyId;
        this.signer = new RSASSASigner((RSAPrivateKey) keyPair.getPrivate());
    }

    public String keyId() {
        return keyId;
    }

    public RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /**
     * The JWKS body the fake server should serve at {@code /.well-known/jwks.json}.
     */
    public Map<String, Object> jwks() {
        RSAKey jwk = new RSAKey.Builder(publicKey())
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new JWKSet(jwk).toJSONObject();
    }

    /**
     * Convenience: sign a JWT with sensible defaults — issuer, vaultId,
     * tenantId, role, scopes, exp = now + 5 minutes.
     */
    public String sign(String issuer, String userId, String tenantId, String role, List<String> scopes) {
        return signWithExpiry(issuer, userId, tenantId, role, scopes, Instant.now().plusSeconds(300));
    }

    public String signWithExpiry(
            String issuer,
            String userId,
            String tenantId,
            String role,
            List<String> scopes,
            Instant expiresAt
    ) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId)
                .claim("vaultId", userId)
                .claim("tenantId", tenantId)
                .claim("role", role)
                .claim("scopes", scopes)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        return sign(claims);
    }

    public String sign(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();
        SignedJWT signed = new SignedJWT(header, claims);
        try {
            signed.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign test JWT", e);
        }
        return signed.serialize();
    }
}
