package com.hess.thevault.apikey;

import com.hess.thevault.apikey.dto.ApiKeyResponse;
import com.hess.thevault.apikey.dto.GenerateApiKeyRequest;
import com.hess.thevault.apikey.dto.GenerateApiKeyResponse;
import com.hess.thevault.auth.VaultUser;
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

    @PostMapping("/api-keys")
    @PreAuthorize("hasAnyRole('ADMIN','TENANT_ADMIN')")
    public GenerateApiKeyResponse generate(
            @Valid @RequestBody GenerateApiKeyRequest request,
            Authentication authentication
    ) {
        return apiKeyService.generate(request, currentUser(authentication));
    }

    @GetMapping("/api-keys")
    public List<ApiKeyResponse> list(Authentication authentication) {
        return apiKeyService.listForUser(currentUser(authentication));
    }

    @DeleteMapping("/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        apiKeyService.revoke(id, currentUser(authentication));
    }

    @GetMapping("/admin/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiKeyResponse> listTenantKeys(Authentication authentication) {
        return apiKeyService.listForTenant(currentUser(authentication));
    }

    private VaultUser currentUser(Authentication authentication) {
        return (VaultUser) authentication.getPrincipal();
    }
}
