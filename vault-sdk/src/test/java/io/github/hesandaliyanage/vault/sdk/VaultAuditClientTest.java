package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.AuditRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;

class VaultAuditClientTest {

    @Test
    void recordedEventIsEventuallySentToServer() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vault.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://vault.test/internal/audit"))
                .andExpect(method(POST))
                .andExpect(header("X-Service-Key", "k"))
                .andExpect(notifying(sent))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        try (VaultAuditClient audit = new VaultAuditClient(builder.build(), "k", new VaultAuditProperties(true, 16))) {
            audit.record(new AuditRequest("t1", "u1", "READ", "/orders/1", "SUCCESS"));
            assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
        }
        server.verify();
    }

    @Test
    void overflowDropsEventsWithoutBlocking() {
        // Worker can't drain (no server mock) so the queue stays full.
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vault.test");
        // No MockRestServiceServer: requests will fail in the worker; that's fine for this test.
        AtomicInteger attempted = new AtomicInteger();
        try (VaultAuditClient audit = new VaultAuditClient(builder.build(), "k", new VaultAuditProperties(true, 2))) {
            for (int i = 0; i < 1000; i++) {
                audit.record(new AuditRequest("t", "u", "A" + i, "/r", "SUCCESS"));
                attempted.incrementAndGet();
            }
        }
        assertThat(attempted.get()).isEqualTo(1000);
    }

    private static RequestMatcher notifying(CountDownLatch latch) {
        return request -> latch.countDown();
    }
}
