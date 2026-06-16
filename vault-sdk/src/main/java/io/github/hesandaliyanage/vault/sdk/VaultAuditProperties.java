package io.github.hesandaliyanage.vault.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the asynchronous audit dispatcher.
 *
 * <pre>
 * vault:
 *   audit:
 *     enabled: true
 *     queue-capacity: 1024
 * </pre>
 *
 * <p>If {@code enabled} is {@code false}, audit calls are silently dropped
 * — the SDK never throws to the caller because of audit failures.
 */
@ConfigurationProperties(prefix = "vault.audit")
public record VaultAuditProperties(
        boolean enabled,
        int queueCapacity
) {

    public VaultAuditProperties {
        if (queueCapacity <= 0) {
            queueCapacity = 1024;
        }
    }
}
