package io.github.hesandaliyanage.vault.protocol;

/**
 * Discriminator for the kind of credential being validated by
 * {@code POST /internal/validate}.
 */
public enum TokenType {
    JWT,
    API_KEY
}
