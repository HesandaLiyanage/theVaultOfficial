package com.vault.demo;

import com.vault.sdk.VaultSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final VaultSecurityContext vaultSecurityContext;

    public DemoController(VaultSecurityContext vaultSecurityContext) {
        this.vaultSecurityContext = vaultSecurityContext;
    }

    @GetMapping("/demo/me")
    public VaultSecurityContext.VaultUser me() {
        return vaultSecurityContext.getCurrentUser();
    }
}
