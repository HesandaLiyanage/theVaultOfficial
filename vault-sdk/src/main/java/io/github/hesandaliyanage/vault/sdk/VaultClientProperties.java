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
 * </pre>
 *
 * <p>{@code base-url} is the only field with no sane default; if it is unset
 * the SDK's auto-configuration leaves itself disabled so applications that
 * pull the dependency in transitively are not forced to configure it.
 */
@ConfigurationProperties(prefix = "vault.client")
public record VaultClientProperties(
        String baseUrl,
        String serviceKey,
        Duration connectTimeout,
        Duration readTimeout
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
