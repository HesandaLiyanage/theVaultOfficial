package io.github.hesandaliyanage.vault.sdk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the HTTP connection from the SDK to a vault-server.
 *
 * <pre>
 * vault:
 *   client:
 *     base-url: https://vault.internal
 *     service-key: ${VAULT_SERVICE_KEY}
 *     connect-timeout: 2s
 *     read-timeout: 5s
 *     allow-insecure-http: false
 * </pre>
 *
 * <p>{@code base-url} is the only field with no sane default; if it is unset
 * the SDK's auto-configuration leaves itself disabled so applications that
 * pull the dependency in transitively are not forced to configure it.
 *
 * <p>{@code allow-insecure-http} must be flipped to {@code true} for any
 * {@code http://} base URL or JWKS URI to be accepted. The default is
 * {@code false}, so a misconfiguration that would otherwise send the service
 * key in cleartext fails fast at startup.
 */
@ConfigurationProperties(prefix = "vault.client")
public record VaultClientProperties(
        String baseUrl,
        String serviceKey,
        Duration connectTimeout,
        Duration readTimeout,
        boolean allowInsecureHttp
) {

    public VaultClientProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
