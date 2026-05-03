package com.vault.demo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterUserRequest(
        @Email
        @NotBlank
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String password,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 200)
        String companyName,

        @NotNull
        UUID tenantId
) {
}
