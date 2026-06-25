package io.github.hesandaliyanage.vault.sdk;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import java.time.Duration;

/**
 * Wraps another {@link TokenValidator} with an in-memory Caffeine cache.
 *
 * <p>Cache hits avoid the network hop to vault-server entirely, which is the
 * point of the SDK keeping a cache at all — every request to a protected
 * endpoint otherwise needs a synchronous round trip. The trade-off is
 * staleness: a token revoked on the server is still treated as valid here
 * until its cache entry expires.
 *
 * <p>Successful and unsuccessful responses use different TTLs:
 * {@code valid=true} entries live for the full {@code ttl}, while
 * {@code valid=false} entries live for a shorter {@code negative-ttl}. That
 * means an attacker spamming bogus tokens fills the cache only briefly with
 * entries that quickly expire, so legitimate cached entries are not evicted
 * en masse.
 *
 * <p>Transport failures (the {@code "vault-server unreachable"} response)
 * are not cached at all, so a brief outage does not poison the cache.
 */
public class CachingVaultClient implements TokenValidator {

    private final TokenValidator delegate;
    private final Cache<CacheKey, ValidateResponse> cache;

    public CachingVaultClient(TokenValidator delegate, VaultCacheProperties properties) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .expireAfter(new ResponseExpiry(properties.ttl(), properties.negativeTtl()))
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

    /**
     * Per-entry expiry that returns the positive TTL for {@code valid=true}
     * responses and the (shorter) negative TTL for {@code valid=false}.
     */
    private static final class ResponseExpiry implements Expiry<CacheKey, ValidateResponse> {
        private final long positiveNanos;
        private final long negativeNanos;

        ResponseExpiry(Duration positiveTtl, Duration negativeTtl) {
            this.positiveNanos = positiveTtl.toNanos();
            this.negativeNanos = negativeTtl.toNanos();
        }

        @Override
        public long expireAfterCreate(CacheKey key, ValidateResponse response, long currentTime) {
            return response.valid() ? positiveNanos : negativeNanos;
        }

        @Override
        public long expireAfterUpdate(CacheKey key, ValidateResponse response, long currentTime, long currentDuration) {
            return response.valid() ? positiveNanos : negativeNanos;
        }

        @Override
        public long expireAfterRead(CacheKey key, ValidateResponse response, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
