package com.vault.sdk.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record GenerateApiKeyRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotEmpty
        List<@NotBlank String> scopes,

        LocalDateTime expiresAt
) {
}
