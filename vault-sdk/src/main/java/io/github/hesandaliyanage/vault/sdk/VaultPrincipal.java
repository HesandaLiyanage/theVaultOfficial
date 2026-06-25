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

    /**
     * Returns a redacted summary safe for accidental log lines. The userId
     * is masked to its last four characters, tenant and role appear as
     * categories (not values), and scopes show only their count.
     *
     * <p>Controllers that need the full identity should access the record
     * components directly rather than relying on {@code toString}.
     */
    @Override
    public String toString() {
        return "VaultPrincipal[userId=" + maskUserId(userId)
                + ", tenantId=" + (tenantId == null ? "<none>" : "<set>")
                + ", role=" + (role == null ? "<none>" : role)
                + ", scopes=" + scopes.size() + "]";
    }

    private static String maskUserId(String userId) {
        if (userId == null) {
            return "<none>";
        }
        if (userId.length() <= 4) {
            return "***";
        }
        return "***" + userId.substring(userId.length() - 4);
    }
}
