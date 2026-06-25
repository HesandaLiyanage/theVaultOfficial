package io.github.hesandaliyanage.vault.sdk.functional;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import io.github.hesandaliyanage.vault.sdk.JwksTokenValidator;
import io.github.hesandaliyanage.vault.sdk.TokenValidator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for the JWKS local-verify mode.
 *
 * <p>Every test uses real RSA-2048 keypairs, a real JWKS endpoint served by
 * {@link FakeVaultServer}, and real RS256-signed JWTs. The SDK fetches the
 * key from the network, verifies signatures locally, and checks claims —
 * exactly as it would in production.
 */
class JwksLocalVerifyFunctionalTest {

    private static final String EXPECTED_ISSUER = "https://vault.test";

    private FakeVaultServer server;
    private JwtTestSigner signer;

    @BeforeEach
    void start() throws Exception {
        server = new FakeVaultServer();
        signer = new JwtTestSigner();
        server.setJwks(signer.jwks());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void validSignedJwtIsAcceptedWithoutCallingRemoteWhenRevocationSkipped() {
        TokenValidator remote = (t, k) -> {
            throw new AssertionError("remote should not be called when skipRemoteRevocationCheck=true");
        };
        JwksTokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                remote,
                true
        );
        String jwt = signer.sign(EXPECTED_ISSUER, "user-42", "tenant-zen", "ADMIN", List.of("orders:read"));

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo("user-42");
        assertThat(response.tenantId()).isEqualTo("tenant-zen");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.scopes()).containsExactly("orders:read");
    }

    @Test
    void validSignedJwtStillCallsRemoteForRevocationCheckByDefault() {
        AtomicInteger remoteCalls = new AtomicInteger();
        TokenValidator remote = (t, k) -> {
            remoteCalls.incrementAndGet();
            return ValidateResponse.success("user-42", "tenant-zen", "ADMIN", List.of());
        };
        JwksTokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                remote,
                false
        );
        String jwt = signer.sign(EXPECTED_ISSUER, "user-42", "tenant-zen", "ADMIN", List.of());

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isTrue();
        assertThat(remoteCalls.get()).isEqualTo(1);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtTestSigner attackerSigner = new JwtTestSigner();
        // Server still publishes only the legitimate signer's JWKS.
        TokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                rejectingRemote(),
                true
        );
        String forged = attackerSigner.sign(EXPECTED_ISSUER, "user-attacker", "tenant-zen", "ADMIN", List.of());

        ValidateResponse response = validator.validate(forged, TokenType.JWT);

        assertThat(response.valid()).isFalse();
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        TokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                rejectingRemote(),
                true
        );
        String jwt = signer.sign("https://attacker.example", "user-42", "tenant-zen", "ADMIN", List.of());

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        TokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                rejectingRemote(),
                true
        );
        String jwt = signer.signWithExpiry(
                EXPECTED_ISSUER,
                "user-42",
                "tenant-zen",
                "ADMIN",
                List.of(),
                Instant.now().minusSeconds(60)
        );

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isFalse();
    }

    @Test
    void tokenMissingIssuerClaimIsRejected() {
        TokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                rejectingRemote(),
                true
        );
        JWTClaimsSet claimsWithoutIss = new JWTClaimsSet.Builder()
                .subject("user-42")
                .claim("vaultId", "user-42")
                .claim("tenantId", "tenant-zen")
                .claim("role", "ADMIN")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        String jwt = signer.sign(claimsWithoutIss);

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isFalse();
    }

    @Test
    void audienceMismatchIsRejected() {
        TokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                "orders-service",
                rejectingRemote(),
                true
        );
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .audience("billing-service")
                .subject("user-42")
                .claim("vaultId", "user-42")
                .claim("tenantId", "tenant-zen")
                .claim("role", "ADMIN")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        String jwt = signer.sign(claims);

        ValidateResponse response = validator.validate(jwt, TokenType.JWT);

        assertThat(response.valid()).isFalse();
    }

    @Test
    void apiKeysAreAlwaysDelegatedRemotely() {
        AtomicInteger remoteCalls = new AtomicInteger();
        TokenValidator remote = (t, k) -> {
            assertThat(k).isEqualTo(TokenType.API_KEY);
            remoteCalls.incrementAndGet();
            return ValidateResponse.success("svc-1", "tenant-zen", "API_KEY", List.of());
        };
        JwksTokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                remote,
                true
        );

        ValidateResponse response = validator.validate("vault_opaque_key", TokenType.API_KEY);

        assertThat(response.valid()).isTrue();
        assertThat(remoteCalls.get()).isEqualTo(1);
    }

    @Test
    void garbageJwtDoesNotCallRemote() {
        AtomicInteger remoteCalls = new AtomicInteger();
        TokenValidator remote = (t, k) -> {
            remoteCalls.incrementAndGet();
            return ValidateResponse.failure("unused");
        };
        JwksTokenValidator validator = new JwksTokenValidator(
                server.jwksUri(),
                EXPECTED_ISSUER,
                null,
                remote,
                false
        );

        ValidateResponse response = validator.validate("not-a-real-jwt", TokenType.JWT);

        assertThat(response.valid()).isFalse();
        // Signature verification failed before remote revocation lookup.
        assertThat(remoteCalls.get()).isZero();
    }

    private static TokenValidator rejectingRemote() {
        return (t, k) -> {
            throw new AssertionError("remote should not be called when local verification rejects");
        };
    }
}
