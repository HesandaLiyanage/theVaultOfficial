package com.vault.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vault")
@Deprecated(since = "0.2.0", forRemoval = true)
public class VaultProperties {

    private final Sdk sdk = new Sdk();
    private final Jwt jwt = new Jwt();
    private final RateLimit rateLimit = new RateLimit();
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/auth/**",
            "/public/**",
            "/actuator/health"
    ));

    public Sdk getSdk() {
        return sdk;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public boolean isEnabled() {
        return sdk.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.sdk.enabled = enabled;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? List.of() : new ArrayList<>(publicPaths);
    }

    public static class Sdk {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Jwt {
        private String secret;
        private long expirationMs = 900_000;
        private long refreshExpirationMs = 604_800_000;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }

        public long getRefreshExpirationMs() {
            return refreshExpirationMs;
        }

        public void setRefreshExpirationMs(long refreshExpirationMs) {
            this.refreshExpirationMs = refreshExpirationMs;
        }
    }

    public static class RateLimit {
        private int limit = 100;
        private long windowSeconds = 60;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
