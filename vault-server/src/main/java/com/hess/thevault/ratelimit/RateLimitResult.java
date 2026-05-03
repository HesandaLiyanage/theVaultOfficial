package com.hess.thevault.ratelimit;

public record RateLimitResult(
        boolean allowed,
        int limit,
        long remaining,
        long resetSeconds
) {
}
