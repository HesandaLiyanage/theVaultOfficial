package io.github.hesandaliyanage.vault.protocol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Wire format for {@code POST /internal/validate}.
 */
public record ValidateRequest(
        @NotBlank
        String token,

        @NotNull
        TokenType type
) {
}
