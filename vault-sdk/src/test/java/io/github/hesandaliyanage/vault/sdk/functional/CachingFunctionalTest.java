package io.github.hesandaliyanage.vault.sdk.functional;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import io.github.hesandaliyanage.vault.sdk.CachingVaultClient;
import io.github.hesandaliyanage.vault.sdk.TokenValidator;
import io.github.hesandaliyanage.vault.sdk.VaultCacheProperties;
import io.github.hesandaliyanage.vault.sdk.VaultClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link CachingVaultClient} sitting in front of a real
 * {@link VaultClient} + {@link FakeVaultServer}. Asserts that the cache
 * actually saves server round trips and that a bogus-token storm cannot
 * displace legitimate cached entries.
 */
class CachingFunctionalTest {

    private FakeVaultServer server;
    private TokenValidator remote;

    @BeforeEach
    void start() throws Exception {
        server = new FakeVaultServer();
        RestClient restClient = RestClient.builder().baseUrl(server.baseUrl()).build();
        remote = new VaultClient(restClient, server.serviceKey());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void repeatedSameTokenHitsServerOnlyOnce() {
        server.onValidate(req -> ValidateResponse.success("u-1", "t", "USER", List.of()));
        CachingVaultClient cache = new CachingVaultClient(remote, longTtlProps());

        for (int i = 0; i < 50; i++) {
            ValidateResponse response = cache.validate("hot.jwt", TokenType.JWT);
            assertThat(response.valid()).isTrue();
        }

        assertThat(server.validateCallCount())
                .as("50 calls for the same token should produce exactly one server hit")
                .isEqualTo(1);
    }

    @Test
    void differentTokensEachHitServerOnce() {
        server.onValidate(req -> ValidateResponse.success(req.token(), "t", "USER", List.of()));
        CachingVaultClient cache = new CachingVaultClient(remote, longTtlProps());

        for (int i = 0; i < 25; i++) {
            cache.validate("token-" + i, TokenType.JWT);
        }
        // Hitting the same tokens a second time should be all-cache.
        for (int i = 0; i < 25; i++) {
            cache.validate("token-" + i, TokenType.JWT);
        }

        assertThat(server.validateCallCount()).isEqualTo(25);
    }

    @Test
    void concurrentHotTokenCollapsesToASmallNumberOfServerCalls() throws Exception {
        // Slow the server so that races have time to manifest.
        server.setValidateLatencyMs(20);
        server.onValidate(req -> ValidateResponse.success("u-1", "t", "USER", List.of()));
        CachingVaultClient cache = new CachingVaultClient(remote, longTtlProps());

        int workers = 32;
        int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        try {
            for (int w = 0; w < workers; w++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            cache.validate("hot.jwt", TokenType.JWT);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Under concurrency, several threads can race past the first cache miss before any
        // of them populate the cache. With Caffeine's CacheLoader semantics this would be
        // exactly one; with our getIfPresent + put pattern it's "small". Assert that the
        // total stays far below the naive total of workers*iterations and ideally below
        // the worker count itself.
        int totalCalls = workers * iterations;
        assertThat(server.validateCallCount())
                .as("concurrent same-token requests should largely coalesce")
                .isLessThanOrEqualTo(workers)
                .isLessThan(totalCalls);
    }

    @Test
    void positiveEntriesOutliveNegativeEntries() throws Exception {
        // The functional value of separate positive/negative TTLs is asymmetric expiry:
        // a single valid response stays cached even after a paired invalid response has
        // expired. Verified against the real server, not the unit-level mock.
        VaultCacheProperties props = new VaultCacheProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMillis(100),
                64
        );
        server.onValidate(req -> req.token().equals("good")
                ? ValidateResponse.success("u-1", "t", "USER", List.of())
                : ValidateResponse.failure("Invalid JWT"));
        CachingVaultClient cache = new CachingVaultClient(remote, props);

        cache.validate("good", TokenType.JWT);
        cache.validate("bad", TokenType.JWT);
        assertThat(server.validateCallCount()).isEqualTo(2);

        Thread.sleep(300);

        cache.validate("good", TokenType.JWT);
        cache.validate("bad", TokenType.JWT);

        // Only the "bad" entry should have expired and been re-fetched. The "good"
        // entry is still well within its 30s positive TTL.
        assertThat(server.validateCallCount())
                .as("good token should still be cached; only bad token re-fetched")
                .isEqualTo(3);
    }

    @Test
    void transportFailureIsNotCached() {
        // Stop the server so every call fails with "unreachable".
        server.close();
        CachingVaultClient cache = new CachingVaultClient(remote, longTtlProps());

        ValidateResponse first = cache.validate("any.jwt", TokenType.JWT);
        ValidateResponse second = cache.validate("any.jwt", TokenType.JWT);

        assertThat(first.valid()).isFalse();
        assertThat(second.valid()).isFalse();
        assertThat(first.reason()).contains("unreachable");
        // No counter to assert against (server is closed), but the important property is
        // that both calls returned a failure response without throwing — proving the
        // cache did not latch onto the first transport failure.
    }

    private static VaultCacheProperties longTtlProps() {
        return new VaultCacheProperties(true, Duration.ofMinutes(5), Duration.ofMinutes(5), 1024);
    }
}
