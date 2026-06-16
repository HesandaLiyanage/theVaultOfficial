package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingVaultClientTest {

    private static VaultCacheProperties props(Duration ttl) {
        return new VaultCacheProperties(true, ttl, 100);
    }

    @Test
    void cachesSuccessfulValidation() {
        AtomicInteger calls = new AtomicInteger();
        TokenValidator delegate = (token, type) -> {
            calls.incrementAndGet();
            return ValidateResponse.success("u1", "t1", "USER", List.of());
        };
        CachingVaultClient cache = new CachingVaultClient(delegate, props(Duration.ofMinutes(1)));

        cache.validate("abc", TokenType.JWT);
        cache.validate("abc", TokenType.JWT);
        cache.validate("abc", TokenType.JWT);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void cachesProtocolFailures() {
        AtomicInteger calls = new AtomicInteger();
        TokenValidator delegate = (token, type) -> {
            calls.incrementAndGet();
            return ValidateResponse.failure("Invalid JWT");
        };
        CachingVaultClient cache = new CachingVaultClient(delegate, props(Duration.ofMinutes(1)));

        cache.validate("bad", TokenType.JWT);
        cache.validate("bad", TokenType.JWT);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotCacheTransportFailures() {
        AtomicInteger calls = new AtomicInteger();
        TokenValidator delegate = (token, type) -> {
            calls.incrementAndGet();
            return ValidateResponse.failure("vault-server unreachable");
        };
        CachingVaultClient cache = new CachingVaultClient(delegate, props(Duration.ofMinutes(1)));

        cache.validate("abc", TokenType.JWT);
        cache.validate("abc", TokenType.JWT);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void differentTokensAreCachedSeparately() {
        AtomicInteger calls = new AtomicInteger();
        TokenValidator delegate = (token, type) -> {
            calls.incrementAndGet();
            return ValidateResponse.success(token, "t1", "USER", List.of());
        };
        CachingVaultClient cache = new CachingVaultClient(delegate, props(Duration.ofMinutes(1)));

        cache.validate("a", TokenType.JWT);
        cache.validate("b", TokenType.JWT);
        cache.validate("a", TokenType.JWT);

        assertThat(calls.get()).isEqualTo(2);
    }
}
