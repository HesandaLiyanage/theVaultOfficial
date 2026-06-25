package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that translates an incoming {@code Authorization: Bearer …}
 * or {@code X-API-Key} header into a Spring Security {@code Authentication}
 * by asking vault-server to validate the credential.
 *
 * <p>The filter is the SDK's main entry point. All token logic — signature,
 * expiry, scopes, revocation — lives on the server; the filter does almost
 * nothing on its own.
 */
public class VaultAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VaultAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator validator;
    private final VaultFilterProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public VaultAuthFilter(TokenValidator validator, VaultFilterProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(properties.apiKeyHeader());
        if (StringUtils.hasText(apiKey)) {
            authenticate(apiKey, TokenType.API_KEY, request, response, chain);
            return;
        }

        String bearer = extractBearer(request);
        if (StringUtils.hasText(bearer)) {
            authenticate(bearer, TokenType.JWT, request, response, chain);
            return;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication");
    }

    private void authenticate(
            String token,
            TokenType type,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        ValidateResponse result = validator.validate(token, type);

        if (!result.valid()) {
            log.debug("vault rejected {}: {}", type, result.reason());
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication");
            return;
        }

        VaultPrincipal principal = new VaultPrincipal(
                result.userId(),
                result.tenantId(),
                result.role(),
                result.scopes()
        );
        SecurityContextHolder.getContext().setAuthentication(new VaultAuthenticationToken(principal));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || hasSuspiciousSegment(path)) {
            // Don't treat a traversal-bearing path as public — force it through auth.
            return false;
        }
        for (String pattern : properties.publicPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Defense-in-depth: refuse to honour public-path patterns for any URI that
     * contains a {@code ..} segment, an encoded dot ({@code %2e}), or an
     * encoded slash ({@code %2f}). Servlet containers normally normalise these
     * away before the filter sees them, but a misconfigured container, a
     * reverse proxy that decodes once but not twice, or a custom filter
     * earlier in the chain can leak them through. Treating any such request as
     * non-public means the worst case is "user must present a valid token to
     * reach the resource," not "attacker bypasses /admin behind /public/**".
     */
    private static boolean hasSuspiciousSegment(String path) {
        if (path.contains("..")) {
            return true;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("%2e") || lower.contains("%2f") || lower.contains("\\");
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
