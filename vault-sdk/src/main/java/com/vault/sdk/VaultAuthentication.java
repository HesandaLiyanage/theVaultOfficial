package com.vault.sdk;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VaultAuthentication extends AbstractAuthenticationToken {

    private final ValidationResponse validation;
    private final String credentialType;

    public VaultAuthentication(ValidationResponse validation, String credentialType) {
        super(authorities(validation));
        this.validation = validation;
        this.credentialType = credentialType;
        setAuthenticated(validation.valid());
    }

    @Override
    public Object getCredentials() {
        return credentialType;
    }

    @Override
    public Object getPrincipal() {
        return validation.userId();
    }

    public String getUserId() {
        return validation.userId();
    }

    public String getTenantId() {
        return validation.tenantId();
    }

    public String getRole() {
        return validation.role();
    }

    public String getApiKeyId() {
        return validation.apiKeyId();
    }

    public String getAuthSource() {
        return validation.authSource();
    }

    public List<String> getScopes() {
        return validation.scopes();
    }

    public ValidationResponse getValidation() {
        return validation;
    }

    private static Collection<? extends GrantedAuthority> authorities(ValidationResponse validation) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (validation.role() != null && !validation.role().isBlank() && !"API_KEY".equals(validation.role())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + validation.role()));
        }

        for (String scope : validation.scopes()) {
            if (scope != null && !scope.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(scope));
            }
        }

        return authorities;
    }
}
