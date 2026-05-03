package com.vault.sdk.ratelimit;

public record RateLimitResult(
        boolean allowed,
        int limit,
        long remaining,
        long resetSeconds
) {
}
