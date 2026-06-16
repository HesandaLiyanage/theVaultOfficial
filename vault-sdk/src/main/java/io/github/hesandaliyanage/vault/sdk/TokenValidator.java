package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;

/**
 * Strategy for validating a JWT or API key. {@link VaultClient} validates
 * against a remote vault-server; {@link CachingVaultClient} wraps another
 * validator with an in-memory cache. The auth filter depends on this
 * interface so the cache is opt-in without code changes.
 */
public interface TokenValidator {

    ValidateResponse validate(String token, TokenType type);
}
