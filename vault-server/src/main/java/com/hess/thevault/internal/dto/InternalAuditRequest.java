package com.hess.thevault.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalAuditRequest(
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
