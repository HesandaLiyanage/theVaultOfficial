package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VaultAuthFilterTest {

    private final VaultFilterProperties props = new VaultFilterProperties(List.of("/health", "/actuator/**"), "X-API-Key");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicPathBypassesValidation() throws Exception {
        TokenValidator failing = (token, type) -> {
            throw new AssertionError("should not be called");
        };
        VaultAuthFilter filter = new VaultAuthFilter(failing, props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void missingCredentialReturns401() throws Exception {
        VaultAuthFilter filter = new VaultAuthFilter(rejectAll(), props);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void validBearerPopulatesSecurityContextAndCallsChain() throws Exception {
        TokenValidator ok = (token, type) -> {
            assertThat(type).isEqualTo(TokenType.JWT);
            return ValidateResponse.success("u1", "t1", "ADMIN", List.of("orders:write"));
        };
        VaultAuthFilter filter = new VaultAuthFilter(ok, props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer abc.def.ghi");
        MockHttpServletResponse res = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.capturedAuth).isNotNull();
        VaultPrincipal principal = (VaultPrincipal) chain.capturedAuth.getPrincipal();
        assertThat(principal.userId()).isEqualTo("u1");
        assertThat(principal.role()).isEqualTo("ADMIN");
        assertThat(principal.scopes()).containsExactly("orders:write");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void apiKeyHeaderIsPreferredOverBearer() throws Exception {
        TokenValidator validator = (token, type) -> {
            assertThat(type).isEqualTo(TokenType.API_KEY);
            assertThat(token).isEqualTo("key-1");
            return ValidateResponse.success("svc-1", "t1", "API_KEY", List.of("read"));
        };
        VaultAuthFilter filter = new VaultAuthFilter(validator, props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("X-API-Key", "key-1");
        req.addHeader("Authorization", "Bearer should-be-ignored");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new CapturingChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidTokenClearsContextAndReturns401() throws Exception {
        VaultAuthFilter filter = new VaultAuthFilter(rejectAll(), props);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer junk");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void publicPathPatternDoesNotMatchTraversal() throws Exception {
        VaultFilterProperties propsWithGlob = new VaultFilterProperties(List.of("/public/**"), "X-API-Key");
        TokenValidator rejectAll = rejectAll();
        VaultAuthFilter filter = new VaultAuthFilter(rejectAll, propsWithGlob);

        for (String suspiciousUri : new String[]{
                "/public/../admin/secret",
                "/public/..",
                "/public/%2e%2e/admin",
                "/public/%2fadmin",
                "/public/\\admin"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", suspiciousUri);
            req.setRequestURI(suspiciousUri);
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, new MockFilterChain());

            assertThat(res.getStatus())
                    .as("path %s must not be treated as public", suspiciousUri)
                    .isEqualTo(401);
        }
    }

    private TokenValidator rejectAll() {
        return (token, type) -> ValidateResponse.failure("nope");
    }

    private static class CapturingChain implements FilterChain {
        boolean invoked;
        Authentication capturedAuth;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            invoked = true;
            capturedAuth = SecurityContextHolder.getContext().getAuthentication();
        }
    }
}
