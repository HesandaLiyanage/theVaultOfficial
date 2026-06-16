package io.github.hesandaliyanage.vault.protocol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire format for {@code POST /internal/audit}. The server treats the call
 * as fire-and-forget (HTTP 202) — clients should not rely on synchronous
 * persistence guarantees.
 */
public record AuditRequest(
        String tenantId,
        String userId,

        @NotBlank
        @Size(max = 100)
        String action,

        @Size(max = 200)
        String resource,

        @Size(max = 20)
        String status
) {
}
