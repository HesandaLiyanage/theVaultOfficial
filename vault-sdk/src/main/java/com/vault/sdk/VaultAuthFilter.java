package com.vault.sdk;

import com.vault.sdk.apikey.ApiKey;
import com.vault.sdk.apikey.ApiKeyService;
import com.vault.sdk.apikey.ApiKeyValidationResult;
import com.vault.sdk.auth.JwtService;
import com.vault.sdk.ratelimit.RateLimitResult;
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

    private static final String AUTH_SOURCE_JWT = "JWT";
    private static final String AUTH_SOURCE_API_KEY = "API_KEY";

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final VaultSecurityContext vaultSecurityContext;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public VaultAuthFilter(
            JwtService jwtService,
            ApiKeyService apiKeyService,
            VaultSecurityContext vaultSecurityContext,
            List<String> publicPaths
    ) {
        this.jwtService = jwtService;
        this.apiKeyService = apiKeyService;
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

        String apiKey = resolveApiKey(request);
        if (StringUtils.hasText(apiKey)) {
            authenticateApiKey(apiKey, response, filterChain, request);
            return;
        }

        String token = resolveBearerToken(request);
        if (!StringUtils.hasText(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication");
            return;
        }

        authenticateJwt(token, response, filterChain, request);
    }

    private void authenticateJwt(
            String token,
            HttpServletResponse response,
            FilterChain filterChain,
            HttpServletRequest request
    ) throws IOException, ServletException {
        if (!jwtService.isTokenValid(token, JwtService.TOKEN_TYPE_ACCESS)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication token");
            return;
        }

        try {
            vaultSecurityContext.setAuthentication(new VaultAuthentication(
                    jwtService.extractVaultId(token),
                    jwtService.extractTenantId(token),
                    jwtService.extractRole(token),
                    List.of(),
                    AUTH_SOURCE_JWT,
                    null,
                    AUTH_SOURCE_JWT
            ));
            filterChain.doFilter(request, response);
        } finally {
            vaultSecurityContext.clear();
        }
    }

    private void authenticateApiKey(
            String apiKey,
            HttpServletResponse response,
            FilterChain filterChain,
            HttpServletRequest request
    ) throws IOException, ServletException {
        ApiKeyValidationResult validation = apiKeyService.validateRawKey(apiKey);
        RateLimitResult rateLimit = validation.rateLimit();
        if (rateLimit != null) {
            addRateLimitHeaders(response, rateLimit);
        }

        if (!validation.valid()) {
            if (rateLimit != null && !rateLimit.allowed()) {
                response.setHeader("Retry-After", String.valueOf(Math.max(rateLimit.resetSeconds() - System.currentTimeMillis() / 1000, 0L)));
                response.sendError(429, validation.reason());
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, validation.reason());
            return;
        }

        ApiKey storedKey = validation.apiKey();
        try {
            vaultSecurityContext.setAuthentication(new VaultAuthentication(
                    storedKey.getCreatedBy(),
                    storedKey.getTenantId(),
                    AUTH_SOURCE_API_KEY,
                    validation.scopes(),
                    AUTH_SOURCE_API_KEY,
                    storedKey.getId().toString(),
                    AUTH_SOURCE_API_KEY
            ));
            filterChain.doFilter(request, response);
        } finally {
            vaultSecurityContext.clear();
        }
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        return null;
    }

    private String resolveApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }

        return null;
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult rateLimit) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimit.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(rateLimit.resetSeconds()));
    }
}
