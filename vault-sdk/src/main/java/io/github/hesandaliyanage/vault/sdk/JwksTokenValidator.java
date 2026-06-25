package io.github.hesandaliyanage.vault.sdk;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates JWTs locally using the server's published JSON Web Key Set.
 *
 * <p>For JWTs, this validator verifies the signature against the public key
 * fetched from {@code vault.client.jwks.uri}, verifies the {@code iss} (and
 * optionally {@code aud}) claim, and then either returns success directly
 * (if {@code skip-remote-revocation-check=true}) or delegates to a remote
 * {@link TokenValidator} for the final revocation check.
 *
 * <p>For API keys, this validator always delegates: there is no local
 * verification path for opaque credentials.
 */
public class JwksTokenValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwksTokenValidator.class);

    private final ConfigurableJWTProcessor<SecurityContext> processor;
    private final TokenValidator remote;
    private final boolean skipRemoteRevocationCheck;

    public JwksTokenValidator(
            URI jwksUri,
            String expectedIssuer,
            String expectedAudience,
            TokenValidator remote,
            boolean skipRemoteRevocationCheck
    ) {
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            throw new IllegalStateException(
                    "vault.client.jwks.expected-issuer must be set when JWKS local verification is enabled. "
                    + "Without it, any token signed with a key from the JWKS would be accepted.");
        }
        this.processor = buildProcessor(jwksUri, expectedIssuer, expectedAudience);
        this.remote = remote;
        this.skipRemoteRevocationCheck = skipRemoteRevocationCheck;
    }

    @Override
    public ValidateResponse validate(String token, TokenType type) {
        if (type == TokenType.API_KEY) {
            return remote.validate(token, type);
        }

        JWTClaimsSet claims;
        try {
            claims = processor.process(token, null);
        } catch (ParseException | com.nimbusds.jose.JOSEException | com.nimbusds.jose.proc.BadJOSEException e) {
            log.debug("JWKS signature/claims rejection: {}", e.getMessage());
            return ValidateResponse.failure("Invalid JWT");
        }

        if (skipRemoteRevocationCheck) {
            return buildResponseFromClaims(claims);
        }

        return remote.validate(token, type);
    }

    private ValidateResponse buildResponseFromClaims(JWTClaimsSet claims) {
        Map<String, Object> raw = claims.getClaims();
        Object scopesObj = raw.get("scopes");
        List<String> scopes;
        if (scopesObj instanceof List<?> list) {
            scopes = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        } else {
            scopes = List.of();
        }
        return ValidateResponse.success(
                stringOrNull(raw.get("vaultId")),
                stringOrNull(raw.get("tenantId")),
                stringOrNull(raw.get("role")),
                scopes
        );
    }

    private static String stringOrNull(Object value) {
        return value instanceof String s ? s : null;
    }

    private static ConfigurableJWTProcessor<SecurityContext> buildProcessor(
            URI jwksUri,
            String expectedIssuer,
            String expectedAudience
    ) {
        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .create(jwksUri.toURL())
                    .build();
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(keySelector);

            JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder()
                    .issuer(expectedIssuer)
                    .build();
            Set<String> requiredClaims = new HashSet<>(Set.of("iss", "exp", "iat"));
            if (expectedAudience != null && !expectedAudience.isBlank()) {
                exactMatchClaims = new JWTClaimsSet.Builder()
                        .issuer(expectedIssuer)
                        .audience(expectedAudience)
                        .build();
                requiredClaims.add("aud");
            }
            processor.setJWTClaimsSetVerifier(
                    new DefaultJWTClaimsVerifier<>(exactMatchClaims, requiredClaims)
            );
            return processor;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid JWKS URI: " + jwksUri, e);
        }
    }
}
