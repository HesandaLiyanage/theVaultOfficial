package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingVaultClientTest {

    private static VaultCacheProperties props(Duration ttl, Duration negativeTtl) {
        return new VaultCacheProperties(true, ttl, negativeTtl, 100);
    }

    private static VaultCacheProperties props(Duration ttl) {
        return props(ttl, Duration.ofSeconds(5));
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

    @Test
    void negativeResponsesUseShorterTtl() throws Exception {
        // Positive TTL: 10s; negative TTL: 100ms. After a 250ms wait, the negative entry must
        // have expired (new delegate call) but the positive entry must still be cached.
        VaultCacheProperties props = props(Duration.ofSeconds(10), Duration.ofMillis(100));

        AtomicInteger positiveCalls = new AtomicInteger();
        TokenValidator positiveDelegate = (token, type) -> {
            positiveCalls.incrementAndGet();
            return ValidateResponse.success("u1", "t1", "USER", List.of());
        };
        CachingVaultClient positiveCache = new CachingVaultClient(positiveDelegate, props);
        positiveCache.validate("p", TokenType.JWT);
        Thread.sleep(250);
        positiveCache.validate("p", TokenType.JWT);
        assertThat(positiveCalls.get())
                .as("positive entry should still be cached after negative-ttl elapsed")
                .isEqualTo(1);

        AtomicInteger negativeCalls = new AtomicInteger();
        TokenValidator negativeDelegate = (token, type) -> {
            negativeCalls.incrementAndGet();
            return ValidateResponse.failure("Invalid JWT");
        };
        CachingVaultClient negativeCache = new CachingVaultClient(negativeDelegate, props);
        negativeCache.validate("n", TokenType.JWT);
        Thread.sleep(250);
        negativeCache.validate("n", TokenType.JWT);
        assertThat(negativeCalls.get())
                .as("negative entry should have expired and been refetched")
                .isEqualTo(2);
    }
}
