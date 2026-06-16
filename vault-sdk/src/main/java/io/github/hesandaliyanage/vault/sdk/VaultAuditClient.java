package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.AuditRequest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fire-and-forget client for {@code POST /internal/audit}. Calls to
 * {@link #record(AuditRequest)} enqueue the event on a bounded in-memory
 * queue and return immediately; a single background worker thread drains
 * the queue and posts to vault-server.
 *
 * <p>If the queue is full at the time of {@code record}, the event is
 * dropped and the drop is logged at WARN. Request threads never block,
 * even if vault-server is slow or unreachable.
 */
public class VaultAuditClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VaultAuditClient.class);
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String AUDIT_PATH = "/internal/audit";

    private final RestClient restClient;
    private final String serviceKey;
    private final BlockingQueue<AuditRequest> queue;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public VaultAuditClient(RestClient restClient, String serviceKey, VaultAuditProperties properties) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.queue = new LinkedBlockingQueue<>(properties.queueCapacity());
        this.worker = new Thread(this::drain, "vault-audit-dispatcher");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void record(AuditRequest event) {
        if (!queue.offer(event)) {
            log.warn("Audit queue full, dropping event action={} userId={}", event.action(), event.userId());
        }
    }

    private void drain() {
        while (running.get() || !queue.isEmpty()) {
            AuditRequest event;
            try {
                event = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (event == null) {
                continue;
            }
            send(event);
        }
    }

    private void send(AuditRequest event) {
        try {
            restClient.post()
                    .uri(AUDIT_PATH)
                    .header(SERVICE_KEY_HEADER, serviceKey)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Audit POST failed action={} reason={}", event.action(), e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);
        worker.interrupt();
        try {
            worker.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
