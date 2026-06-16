package io.github.hesandaliyanage.vault.protocol;

import java.util.List;

/**
 * Wire format returned by {@code POST /internal/validate}.
 * On success, {@link #valid()} is {@code true} and identity fields are populated.
 * On failure, {@link #valid()} is {@code false}, identity fields are {@code null},
 * and {@link #reason()} carries a short human-readable explanation.
 */
public record ValidateResponse(
        boolean valid,
        String userId,
        String tenantId,
        String role,
        List<String> scopes,
        String reason
) {

    public static ValidateResponse success(
            String userId,
            String tenantId,
            String role,
            List<String> scopes
    ) {
        return new ValidateResponse(true, userId, tenantId, role, scopes, null);
    }

    public static ValidateResponse failure(String reason) {
        return new ValidateResponse(false, null, null, null, List.of(), reason);
    }
}
