package com.vault.sdk;

import org.springframework.web.client.RestClient;

public class VaultClient {

    private final VaultProperties properties;
    private final RestClient restClient;

    public VaultClient(VaultProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .defaultHeader("X-Vault-Api-Key", properties.getApiKey() == null ? "" : properties.getApiKey())
                .build();
    }

    public VaultSecurityContext.VaultUser validateToken(String token) {
        // TODO: Call vault-server's internal validation endpoint when it exists.
        return new VaultSecurityContext.VaultUser(null, null, null, token);
    }

    public VaultProperties getProperties() {
        return properties;
    }
}
