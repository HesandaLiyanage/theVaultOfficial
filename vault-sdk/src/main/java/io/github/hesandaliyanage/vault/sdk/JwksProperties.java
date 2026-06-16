package io.github.hesandaliyanage.vault.sdk;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in configuration for JWKS-based local JWT verification.
 *
 * <pre>
 * vault:
 *   client:
 *     jwks:
 *       uri: https://vault.internal/.well-known/jwks.json
 *       skip-remote-revocation-check: false
 * </pre>
 *
 * <p>When {@code uri} is set, the SDK switches its JWT validation path
 * from "always call vault-server" to "verify signature locally with the
 * server's published public key." Signature verification is fast (no
 * round trip) but does not catch revocations on its own, so the SDK
 * still calls {@code /internal/validate} for each token unless
 * {@code skip-remote-revocation-check} is {@code true}.
 *
 * <p>Setting {@code skip-remote-revocation-check=true} is a real
 * trade-off: tokens stay valid until expiry even if the user logs out
 * or the token is added to the server-side blacklist. Only set it when
 * short access-token TTLs make revocation gaps acceptable.
 *
 * <p>API-key validation is always remote; this property only affects JWTs.
 */
@ConfigurationProperties(prefix = "vault.client.jwks")
public record JwksProperties(
        URI uri,
        boolean skipRemoteRevocationCheck
) {
}
