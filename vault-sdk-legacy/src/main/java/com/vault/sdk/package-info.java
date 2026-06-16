/**
 * Legacy in-process Spring Boot starter from v0.1.x.
 *
 * <p>The classes in this package and its sub-packages run JWT signing,
 * API-key storage, audit logging and rate limiting inside the consumer
 * application. They are preserved for one minor release to give v0.1.x
 * users time to migrate, and will be removed in v0.3.0.
 *
 * <p>New code should depend on {@code vault-sdk} (the thin client) and
 * run {@code vault-server} as a separate service.
 *
 * @deprecated since 0.2.0, scheduled for removal in 0.3.0. Migrate to
 *             {@code io.github.hesandaliyanage:vault-sdk}.
 */
@Deprecated(since = "0.2.0", forRemoval = true)
package com.vault.sdk;
