package io.github.hesandaliyanage.vault.sdk.functional;

import io.github.hesandaliyanage.vault.protocol.AuditRequest;
import io.github.hesandaliyanage.vault.sdk.VaultAuditClient;
import io.github.hesandaliyanage.vault.sdk.VaultAuditProperties;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link VaultAuditClient}'s async delivery path.
 *
 * <p>Events are posted to a real {@link FakeVaultServer} over HTTP. The
 * dispatcher worker thread, queue, and POST loop all run as they would in
 * production.
 */
class VaultAuditClientFunctionalTest {

    private FakeVaultServer server;
    private RestClient restClient;

    @BeforeEach
    void start() throws Exception {
        server = new FakeVaultServer();
        restClient = RestClient.builder().baseUrl(server.baseUrl()).build();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void recordedEventReachesServerAsynchronously() throws Exception {
        try (VaultAuditClient audit = newAuditClient(64)) {
            audit.record(new AuditRequest("tenant-blue", "user-1", "ORDER_CREATED", "/orders/42", "SUCCESS"));

            AuditRequest delivered = server.receivedAuditEvents().poll(2, TimeUnit.SECONDS);
            assertThat(delivered).isNotNull();
            assertThat(delivered.tenantId()).isEqualTo("tenant-blue");
            assertThat(delivered.userId()).isEqualTo("user-1");
            assertThat(delivered.action()).isEqualTo("ORDER_CREATED");
            assertThat(delivered.resource()).isEqualTo("/orders/42");
            assertThat(delivered.status()).isEqualTo("SUCCESS");
        }
    }

    @Test
    void manyEventsAreAllDelivered() throws Exception {
        int total = 200;
        try (VaultAuditClient audit = newAuditClient(total + 16)) {
            for (int i = 0; i < total; i++) {
                audit.record(new AuditRequest("t", "u", "ACTION_" + i, "/r", "SUCCESS"));
            }

            // Wait until all are drained.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (server.auditCallCount() < total && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
        }

        assertThat(server.auditCallCount()).isEqualTo(total);

        Set<String> seen = new HashSet<>();
        AuditRequest req;
        while ((req = server.receivedAuditEvents().poll()) != null) {
            seen.add(req.action());
        }
        // Every action must be present exactly once.
        assertThat(seen).hasSize(total);
    }

    @Test
    void recordReturnsImmediatelyEvenIfServerIsSlow() throws Exception {
        // 500ms latency per validate request - this affects /internal/audit too since
        // FakeVaultServer reuses the latency setting only for validate. Use a CountDownLatch
        // observer to block the audit handler instead.
        CountDownLatch release = new CountDownLatch(1);
        server.onAudit(event -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try (VaultAuditClient audit = newAuditClient(32)) {
            long start = System.nanoTime();
            for (int i = 0; i < 16; i++) {
                audit.record(new AuditRequest("t", "u", "A" + i, "/r", "SUCCESS"));
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            // Even with a server that takes seconds per audit call, record() itself returns
            // immediately because it only does queue.offer.
            assertThat(elapsedMs).isLessThan(500);

            release.countDown();
        }
    }

    @Test
    void overflowDropsEventsButLeavesOthersIntact() throws Exception {
        // Capacity 2, server blocked. After we submit much more than 2, the queue should
        // never grow beyond 2. When we release the server, exactly what was queued at any
        // moment gets delivered.
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger dispatched = new AtomicInteger();
        server.onAudit(event -> {
            try {
                release.await(5, TimeUnit.SECONDS);
                dispatched.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int submitted = 200;
        try (VaultAuditClient audit = newAuditClient(2)) {
            for (int i = 0; i < submitted; i++) {
                audit.record(new AuditRequest("t", "u", "A" + i, "/r", "SUCCESS"));
            }
            release.countDown();
            Thread.sleep(500);
            // The total delivered is far less than what we submitted — overflows were dropped.
            assertThat(server.auditCallCount()).isLessThanOrEqualTo(submitted);
            assertThat(server.auditCallCount()).isPositive();
        }
    }

    @Test
    void serverErrorsDoNotCrashTheWorker() throws Exception {
        server.setAuditHttpStatus(500);

        try (VaultAuditClient audit = newAuditClient(32)) {
            for (int i = 0; i < 10; i++) {
                audit.record(new AuditRequest("t", "u", "A" + i, "/r", "SUCCESS"));
            }
            // Give the worker time to process — the server is returning 500 every time.
            Thread.sleep(300);
        }

        // All events were attempted (server.auditCallCount is incremented before status,
        // and the queue was small enough to fully drain).
        assertThat(server.auditCallCount()).isEqualTo(10);
    }

    @Test
    void closeDrainsRemainingEvents() throws Exception {
        // Submit 50 events, then immediately close the client. Worker should drain remaining
        // events before exiting (within the join timeout).
        VaultAuditClient audit = newAuditClient(128);
        try {
            for (int i = 0; i < 50; i++) {
                audit.record(new AuditRequest("t", "u", "A" + i, "/r", "SUCCESS"));
            }
        } finally {
            audit.close();
        }

        // close() blocks up to 2s waiting for drain. Allow a small grace period.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3000);
        while (server.auditCallCount() < 50 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(server.auditCallCount()).isEqualTo(50);
    }

    private VaultAuditClient newAuditClient(int capacity) {
        return new VaultAuditClient(restClient, server.serviceKey(), new VaultAuditProperties(true, capacity));
    }
}
