package com.hess.thevault.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalValidateRequest(
        @NotBlank
        String token,

        @NotNull
        InternalTokenType type
) {
}
