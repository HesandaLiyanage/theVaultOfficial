package io.github.hesandaliyanage.vault.sdk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the in-memory cache that sits in front of {@link VaultClient}.
 *
 * <pre>
 * vault:
 *   client:
 *     cache:
 *       enabled: true
 *       ttl: 30s
 *       negative-ttl: 5s
 *       max-size: 10000
 * </pre>
 *
 * <p>Disabled by default for safety — turning it on is an explicit decision
 * that accepts at most {@code ttl} of staleness between a token being
 * revoked on the server and the SDK noticing.
 *
 * <p>{@code ttl} controls how long {@code valid=true} responses are cached.
 * {@code negative-ttl} controls how long {@code valid=false} responses (real
 * rejections from vault-server, not transport failures) are cached. The
 * negative TTL defaults to a fraction of {@code ttl} so an attacker spamming
 * bogus tokens cannot dominate the cache and evict legitimate entries for
 * the full window — bogus-token entries expire quickly and the cache
 * recycles capacity to real users.
 */
@ConfigurationProperties(prefix = "vault.client.cache")
public record VaultCacheProperties(
        boolean enabled,
        Duration ttl,
        Duration negativeTtl,
        long maxSize
) {

    public VaultCacheProperties {
        if (ttl == null) {
            ttl = Duration.ofSeconds(30);
        }
        if (negativeTtl == null) {
            negativeTtl = Duration.ofSeconds(5);
        }
        if (maxSize <= 0) {
            maxSize = 10_000L;
        }
    }
}
