package io.github.hesandaliyanage.vault.sdk.functional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.hesandaliyanage.vault.protocol.AuditRequest;
import io.github.hesandaliyanage.vault.protocol.ValidateRequest;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * In-process HTTP server used by the functional test suite. Stands in for
 * a real vault-server: exposes {@code /internal/validate},
 * {@code /internal/audit}, and {@code /.well-known/jwks.json}, with each
 * endpoint's behaviour configurable per-test.
 *
 * <p>Built on the JDK's {@link com.sun.net.httpserver.HttpServer} so the
 * tests have no extra runtime dependency. Tests bind to an ephemeral port
 * and read it back via {@link #baseUrl()}.
 */
public final class FakeVaultServer implements AutoCloseable {

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<Function<ValidateRequest, ValidateResponse>> validateHandler =
            new AtomicReference<>(req -> ValidateResponse.failure("not configured"));
    private final AtomicReference<Consumer<AuditRequest>> auditObserver =
            new AtomicReference<>(req -> { });
    private final AtomicReference<Map<String, Object>> jwksBody = new AtomicReference<>();
    private final AtomicReference<String> requiredServiceKey =
            new AtomicReference<>("test-service-key");
    private final AtomicReference<Integer> validateLatencyMs = new AtomicReference<>(0);
    private final AtomicReference<Integer> auditHttpStatus = new AtomicReference<>(202);

    private final AtomicInteger validateCalls = new AtomicInteger();
    private final AtomicInteger auditCalls = new AtomicInteger();
    private final AtomicInteger jwksCalls = new AtomicInteger();
    private final BlockingQueue<AuditRequest> receivedAudit = new LinkedBlockingQueue<>();

    public FakeVaultServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/validate", this::handleValidate);
        server.createContext("/internal/audit", this::handleAudit);
        server.createContext("/.well-known/jwks.json", this::handleJwks);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        InetSocketAddress addr = server.getAddress();
        return "http://" + addr.getHostString() + ":" + addr.getPort();
    }

    public URI jwksUri() {
        return URI.create(baseUrl() + "/.well-known/jwks.json");
    }

    public String serviceKey() {
        return requiredServiceKey.get();
    }

    public void setServiceKey(String key) {
        requiredServiceKey.set(key);
    }

    public void onValidate(Function<ValidateRequest, ValidateResponse> handler) {
        validateHandler.set(handler);
    }

    public void onAudit(Consumer<AuditRequest> observer) {
        auditObserver.set(observer);
    }

    public void setAuditHttpStatus(int status) {
        auditHttpStatus.set(status);
    }

    public void setValidateLatencyMs(int millis) {
        validateLatencyMs.set(millis);
    }

    public void setJwks(Map<String, Object> body) {
        jwksBody.set(body);
    }

    public int validateCallCount() {
        return validateCalls.get();
    }

    public int auditCallCount() {
        return auditCalls.get();
    }

    public int jwksCallCount() {
        return jwksCalls.get();
    }

    public BlockingQueue<AuditRequest> receivedAuditEvents() {
        return receivedAudit;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleValidate(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String required = requiredServiceKey.get();
            String received = exchange.getRequestHeaders().getFirst("X-Service-Key");
            if (required != null && !required.equals(received)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            int delay = validateLatencyMs.get();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ValidateRequest request = mapper.readValue(exchange.getRequestBody(), ValidateRequest.class);
            validateCalls.incrementAndGet();
            ValidateResponse response = validateHandler.get().apply(request);
            byte[] body = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private void handleAudit(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String required = requiredServiceKey.get();
            String received = exchange.getRequestHeaders().getFirst("X-Service-Key");
            if (required != null && !required.equals(received)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            AuditRequest request = mapper.readValue(exchange.getRequestBody(), AuditRequest.class);
            auditCalls.incrementAndGet();
            receivedAudit.offer(request);
            auditObserver.get().accept(request);
            int status = auditHttpStatus.get();
            exchange.sendResponseHeaders(status, -1);
        } finally {
            exchange.close();
        }
    }

    private void handleJwks(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, Object> body = jwksBody.get();
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            jwksCalls.incrementAndGet();
            byte[] bytes = mapper.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
