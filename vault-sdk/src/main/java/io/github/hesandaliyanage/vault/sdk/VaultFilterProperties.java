package io.github.hesandaliyanage.vault.sdk;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link VaultAuthFilter}.
 *
 * <pre>
 * vault:
 *   filter:
 *     public-paths:
 *       - /health
 *       - /actuator/**
 *     api-key-header: X-API-Key
 * </pre>
 */
@ConfigurationProperties(prefix = "vault.filter")
public record VaultFilterProperties(
        List<String> publicPaths,
        String apiKeyHeader
) {

    public VaultFilterProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            apiKeyHeader = "X-API-Key";
        }
    }
}
