package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwksTokenValidatorTest {

    private static final URI FAKE_JWKS = URI.create("http://localhost:1/jwks.json");

    @Test
    void apiKeyValidationIsAlwaysDelegated() {
        TokenValidator remote = (token, type) -> {
            assertThat(type).isEqualTo(TokenType.API_KEY);
            return ValidateResponse.success("svc-1", "t1", "API_KEY", List.of("read"));
        };
        JwksTokenValidator validator = new JwksTokenValidator(FAKE_JWKS, remote, true);

        ValidateResponse response = validator.validate("ak-123", TokenType.API_KEY);

        assertThat(response.valid()).isTrue();
        assertThat(response.role()).isEqualTo("API_KEY");
    }

    @Test
    void garbageJwtIsRejected() {
        TokenValidator remote = (token, type) -> {
            throw new AssertionError("remote should not be called for garbage JWT");
        };
        JwksTokenValidator validator = new JwksTokenValidator(FAKE_JWKS, remote, true);

        ValidateResponse response = validator.validate("not-a-jwt", TokenType.JWT);

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("signature");
    }
}
