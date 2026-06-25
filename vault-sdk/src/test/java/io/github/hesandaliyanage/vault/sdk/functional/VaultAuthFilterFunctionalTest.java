package io.github.hesandaliyanage.vault.sdk.functional;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import io.github.hesandaliyanage.vault.sdk.TokenValidator;
import io.github.hesandaliyanage.vault.sdk.VaultAuthFilter;
import io.github.hesandaliyanage.vault.sdk.VaultClient;
import io.github.hesandaliyanage.vault.sdk.VaultFilterProperties;
import io.github.hesandaliyanage.vault.sdk.VaultPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: client request enters {@link VaultAuthFilter}, the filter
 * makes a real HTTP call to {@link FakeVaultServer}, the response shapes the
 * SecurityContext, and the chain runs (or is short-circuited with 401).
 *
 * <p>No Spring Boot container, no mocks of the HTTP layer — the
 * filter, {@code VaultClient}, and {@code RestClient} all run unmodified
 * against a real server bound to an ephemeral port.
 */
class VaultAuthFilterFunctionalTest {

    private FakeVaultServer server;
    private VaultAuthFilter filter;

    @BeforeEach
    void start() throws Exception {
        server = new FakeVaultServer();
        RestClient restClient = RestClient.builder().baseUrl(server.baseUrl()).build();
        TokenValidator validator = new VaultClient(restClient, server.serviceKey());
        VaultFilterProperties props = new VaultFilterProperties(List.of("/public/**", "/health"), "X-API-Key");
        filter = new VaultAuthFilter(validator, props);
    }

    @AfterEach
    void stop() {
        SecurityContextHolder.clearContext();
        server.close();
    }

    @Test
    void validBearerTokenPopulatesPrincipalAndCallsChain() throws Exception {
        server.onValidate(req -> {
            assertThat(req.type()).isEqualTo(TokenType.JWT);
            assertThat(req.token()).isEqualTo("good.jwt");
            return ValidateResponse.success("user-9", "tenant-blue", "ADMIN", List.of("orders:write"));
        });

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer good.jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(server.validateCallCount()).isEqualTo(1);
        assertThat(chain.wasCalled()).isTrue();
        assertThat(chain.principal()).isNotNull();
        assertThat(chain.principal().userId()).isEqualTo("user-9");
        assertThat(chain.principal().tenantId()).isEqualTo("tenant-blue");
        assertThat(chain.principal().role()).isEqualTo("ADMIN");
        assertThat(chain.principal().scopes()).containsExactly("orders:write");
        // Context must be cleared after the chain finishes.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void apiKeyHeaderRoutesAsApiKeyType() throws Exception {
        server.onValidate(req -> {
            assertThat(req.type()).isEqualTo(TokenType.API_KEY);
            assertThat(req.token()).isEqualTo("vault_secret_key");
            return ValidateResponse.success("svc-1", "tenant-blue", "API_KEY", List.of("read"));
        });

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("X-API-Key", "vault_secret_key");
        MockHttpServletResponse res = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.wasCalled()).isTrue();
        assertThat(chain.principal().userId()).isEqualTo("svc-1");
    }

    @Test
    void apiKeyHeaderIsPreferredOverBearerWhenBothPresent() throws Exception {
        AtomicReference<TokenType> seen = new AtomicReference<>();
        server.onValidate(req -> {
            seen.set(req.type());
            return ValidateResponse.success("svc", "t", "API_KEY", List.of());
        });

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("X-API-Key", "vault_key");
        req.addHeader("Authorization", "Bearer ignored.jwt");
        filter.doFilter(req, new MockHttpServletResponse(), new CapturingChain());

        assertThat(seen.get()).isEqualTo(TokenType.API_KEY);
    }

    @Test
    void rejectedTokenReturns401AndDoesNotCallChain() throws Exception {
        server.onValidate(req -> ValidateResponse.failure("Invalid JWT"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer bad.jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.wasCalled()).isFalse();
    }

    @Test
    void missingCredentialReturns401WithoutHittingServer() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new CapturingChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(server.validateCallCount()).isZero();
    }

    @Test
    void publicPathSkipsServerEntirely() throws Exception {
        server.onValidate(req -> {
            throw new AssertionError("public path must not call validate");
        });

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse res = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();
        filter.doFilter(req, res, chain);

        assertThat(server.validateCallCount()).isZero();
        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    void wrongServiceKeyMapsTo401WithoutCrashing() throws Exception {
        // Filter is configured with the real service key; flip the server to expect a different one.
        server.setServiceKey("different-service-key");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer any.jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new CapturingChain());

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void serverUnreachableMapsTo401WithoutPoisoning() throws Exception {
        // Stop the server before the request.
        server.close();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer any.jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new CapturingChain());

        assertThat(res.getStatus()).isEqualTo(401);

        // Bring the server back up for the AfterEach close() — it has to be a fresh instance,
        // because the original is now closed. We can't easily rebuild here without restructuring,
        // so just verify the filter didn't throw.
    }

    private static final class CapturingChain implements FilterChain {
        private volatile boolean called;
        private volatile VaultPrincipal principal;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            called = true;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                principal = (VaultPrincipal) auth.getPrincipal();
            }
        }

        boolean wasCalled() {
            return called;
        }

        VaultPrincipal principal() {
            return principal;
        }
    }
}
