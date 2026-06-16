/**
 * Vault SDK — a thin HTTP client that lets a Spring Boot application
 * delegate authentication and audit recording to a standalone
 * {@code vault-server}.
 *
 * <p>The SDK contributes:
 * <ul>
 *   <li>{@code VaultClient}, an HTTP client for {@code /internal/validate}.</li>
 *   <li>{@code VaultAuthFilter}, a servlet filter that resolves
 *       {@code Authorization: Bearer …} or {@code X-API-Key} headers, asks
 *       the server to validate them, and populates Spring's
 *       {@link org.springframework.security.core.context.SecurityContextHolder}.</li>
 *   <li>{@code VaultAuditClient}, an asynchronous client for
 *       {@code /internal/audit}.</li>
 * </ul>
 *
 * <p>No JWT signing, API-key storage, audit logging or rate limiting runs
 * inside the consumer application; all of that lives in {@code vault-server}.
 */
package io.github.hesandaliyanage.vault.sdk;
