package io.github.hesandaliyanage.vault.sdk;

import java.util.List;

/**
 * Identity returned by vault-server after a successful validation.
 * Stored as the principal on Spring's {@link org.springframework.security.core.Authentication}
 * so controllers can read it with
 * {@code (VaultPrincipal) authentication.getPrincipal()}.
 */
public record VaultPrincipal(
        String userId,
        String tenantId,
        String role,
        List<String> scopes
) {

    public VaultPrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
