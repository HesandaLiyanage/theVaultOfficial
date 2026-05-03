package com.hess.thevault.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HashPasswordRequest(
        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {
}
