package com.vault.sdk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class VaultAuthFilter extends OncePerRequestFilter {

    private final VaultClient vaultClient;
    private final VaultSecurityContext vaultSecurityContext;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public VaultAuthFilter(
            VaultClient vaultClient,
            VaultSecurityContext vaultSecurityContext,
            List<String> publicPaths
    ) {
        this.vaultClient = vaultClient;
        this.vaultSecurityContext = vaultSecurityContext;
        this.publicPaths = publicPaths;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ResolvedToken resolvedToken = resolveToken(request);
        if (resolvedToken == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication");
            return;
        }

        ValidationResponse validation;
        try {
            validation = vaultClient.validate(resolvedToken.token());
        } catch (RuntimeException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication validation failed");
            return;
        }

        if (validation == null || !validation.valid()) {
            String reason = validation == null || !StringUtils.hasText(validation.reason())
                    ? "Invalid authentication"
                    : validation.reason();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, reason);
            return;
        }

        try {
            vaultSecurityContext.setAuthentication(new VaultAuthentication(validation, resolvedToken.type()));
            filterChain.doFilter(request, response);
        } finally {
            vaultClient.auditAsync(request, validation, response.getStatus());
            vaultSecurityContext.clear();
        }
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private ResolvedToken resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (StringUtils.hasText(token)) {
                return new ResolvedToken(token, "JWT");
            }
        }

        String apiKey = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKey)) {
            return new ResolvedToken(apiKey.trim(), "API_KEY");
        }

        return null;
    }

    private record ResolvedToken(String token, String type) {
    }
}
