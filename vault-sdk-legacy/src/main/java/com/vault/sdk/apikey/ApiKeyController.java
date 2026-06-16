package com.vault.sdk.apikey;

import com.vault.sdk.VaultAuthentication;
import com.vault.sdk.apikey.dto.ApiKeyResponse;
import com.vault.sdk.apikey.dto.GenerateApiKeyRequest;
import com.vault.sdk.apikey.dto.GenerateApiKeyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/vault/api-keys")
    @PreAuthorize("hasAnyRole('ADMIN','TENANT_ADMIN')")
    public GenerateApiKeyResponse generate(@Valid @RequestBody GenerateApiKeyRequest request, Authentication authentication) {
        VaultAuthentication current = current(authentication);
        return apiKeyService.generate(request, current.getUserId(), current.getTenantId());
    }

    @GetMapping("/vault/api-keys")
    public List<ApiKeyResponse> list(Authentication authentication) {
        VaultAuthentication current = current(authentication);
        return apiKeyService.listForUser(current.getTenantId(), current.getUserId());
    }

    @DeleteMapping("/vault/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        VaultAuthentication current = current(authentication);
        apiKeyService.revoke(id, current.getUserId(), current.getTenantId(), current.getRole());
    }

    @GetMapping("/vault/admin/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiKeyResponse> listTenantKeys(Authentication authentication) {
        VaultAuthentication current = current(authentication);
        return apiKeyService.listForTenant(current.getTenantId());
    }

    private VaultAuthentication current(Authentication authentication) {
        return (VaultAuthentication) authentication;
    }
}
