package io.github.hesandaliyanage.vault.sdk;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;

/**
 * Wraps another {@link TokenValidator} with an in-memory Caffeine cache.
 *
 * <p>Cache hits avoid the network hop to vault-server entirely, which is the
 * point of the SDK keeping a cache at all — every request to a protected
 * endpoint otherwise needs a synchronous round trip. The trade-off is
 * staleness: a token revoked on the server is still treated as valid here
 * until its cache entry expires.
 *
 * <p>Transport failures (the {@code "vault-server unreachable"} response)
 * are not cached, so a brief outage does not poison the cache. Real
 * server responses — both {@code valid=true} and {@code valid=false} — are
 * cached for the configured TTL.
 */
public class CachingVaultClient implements TokenValidator {

    private final TokenValidator delegate;
    private final Cache<CacheKey, ValidateResponse> cache;

    public CachingVaultClient(TokenValidator delegate, VaultCacheProperties properties) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maxSize())
                .build();
    }

    @Override
    public ValidateResponse validate(String token, TokenType type) {
        CacheKey key = new CacheKey(type, token);
        ValidateResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        ValidateResponse fresh = delegate.validate(token, type);
        if (isCacheable(fresh)) {
            cache.put(key, fresh);
        }
        return fresh;
    }

    private boolean isCacheable(ValidateResponse response) {
        if (response.valid()) {
            return true;
        }
        String reason = response.reason();
        return reason != null && !reason.contains("unreachable");
    }

    private record CacheKey(TokenType type, String token) {
    }
}
