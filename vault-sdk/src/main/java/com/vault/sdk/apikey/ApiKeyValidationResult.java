package com.vault.sdk.apikey;

import com.vault.sdk.ratelimit.RateLimitResult;

import java.util.List;

public record ApiKeyValidationResult(
        boolean valid,
        String reason,
        ApiKey apiKey,
        List<String> scopes,
        RateLimitResult rateLimit
) {

    public static ApiKeyValidationResult success(ApiKey apiKey, List<String> scopes, RateLimitResult rateLimit) {
        return new ApiKeyValidationResult(true, null, apiKey, scopes, rateLimit);
    }

    public static ApiKeyValidationResult failure(String reason) {
        return new ApiKeyValidationResult(false, reason, null, List.of(), null);
    }
}
