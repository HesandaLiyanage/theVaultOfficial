package com.hess.thevault.ratelimit;

import com.hess.thevault.apikey.ApiKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final int limit;
    private final long windowSeconds;

    public RateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${vault.api-keys.rate-limit.limit}") int limit,
            @Value("${vault.api-keys.rate-limit.window-seconds}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public RateLimitResult consume(ApiKey apiKey) {
        String tokensKey = "ratelimit:%s:%s:tokens".formatted(apiKey.getTenantId(), apiKey.getId());
        String resetKey = "ratelimit:%s:%s:reset".formatted(apiKey.getTenantId(), apiKey.getId());

        Boolean initialized = redisTemplate.opsForValue()
                .setIfAbsent(tokensKey, String.valueOf(limit - 1), Duration.ofSeconds(windowSeconds));

        if (Boolean.TRUE.equals(initialized)) {
            long resetAt = System.currentTimeMillis() / 1000 + windowSeconds;
            redisTemplate.opsForValue().set(resetKey, String.valueOf(resetAt), Duration.ofSeconds(windowSeconds));
            return new RateLimitResult(true, limit, limit - 1L, resetAt);
        }

        Long remaining = redisTemplate.opsForValue().decrement(tokensKey);
        long safeRemaining = remaining == null ? -1L : remaining;
        long resetAt = readReset(resetKey);

        return new RateLimitResult(safeRemaining >= 0, limit, Math.max(safeRemaining, 0L), resetAt);
    }

    private long readReset(String resetKey) {
        String value = redisTemplate.opsForValue().get(resetKey);
        if (value == null) {
            return System.currentTimeMillis() / 1000 + windowSeconds;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return System.currentTimeMillis() / 1000 + windowSeconds;
        }
    }
}
