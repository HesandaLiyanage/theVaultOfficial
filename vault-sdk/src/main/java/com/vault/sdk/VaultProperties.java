package com.vault.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    private final Sdk sdk = new Sdk();
    private boolean enabled = true;
    private String serverUrl = "http://localhost:8081";
    private String apiKey;
    private String baseUrl = "http://localhost:8080";
    private String serviceApiKey;
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/public/**",
            "/actuator/health",
            "/users/register"
    ));

    public Sdk getSdk() {
        return sdk;
    }

    public boolean isEnabled() {
        return sdk.enabled && enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.sdk.enabled = enabled;
    }

    public String getServerUrl() {
        return baseUrl != null ? baseUrl : serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        this.baseUrl = serverUrl;
    }

    public String getApiKey() {
        return serviceApiKey != null ? serviceApiKey : apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.serviceApiKey = apiKey;
    }

    public String getBaseUrl() {
        return getServerUrl();
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        this.serverUrl = baseUrl;
    }

    public String getServiceApiKey() {
        return getApiKey();
    }

    public void setServiceApiKey(String serviceApiKey) {
        this.serviceApiKey = serviceApiKey;
        this.apiKey = serviceApiKey;
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
}
