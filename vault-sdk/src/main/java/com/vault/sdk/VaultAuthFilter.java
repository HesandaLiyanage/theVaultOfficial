package com.vault.sdk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class VaultAuthFilter extends OncePerRequestFilter {

    private final VaultClient vaultClient;
    private final VaultSecurityContext vaultSecurityContext;

    public VaultAuthFilter(VaultClient vaultClient, VaultSecurityContext vaultSecurityContext) {
        this.vaultClient = vaultClient;
        this.vaultSecurityContext = vaultSecurityContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);

        if (token != null) {
            VaultSecurityContext.VaultUser user = vaultClient.validateToken(token);
            vaultSecurityContext.setCurrentUser(user);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            vaultSecurityContext.clear();
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }
}
