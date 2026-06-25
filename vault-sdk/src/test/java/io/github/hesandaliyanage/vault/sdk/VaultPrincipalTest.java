package io.github.hesandaliyanage.vault.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VaultPrincipalTest {

    @Test
    void toStringMasksUserIdAndOmitsTenantAndScopes() {
        VaultPrincipal principal = new VaultPrincipal(
                "user-1234-secret-id",
                "tenant-acme",
                "ADMIN",
                List.of("orders:read", "orders:write")
        );

        String text = principal.toString();

        assertThat(text).doesNotContain("user-1234-secret-id");
        assertThat(text).doesNotContain("tenant-acme");
        assertThat(text).doesNotContain("orders:read");
        assertThat(text).contains("***t-id");
        assertThat(text).contains("scopes=2");
        assertThat(text).contains("ADMIN");
    }

    @Test
    void toStringHandlesNullsAndShortIds() {
        VaultPrincipal nullEverything = new VaultPrincipal(null, null, null, null);
        assertThat(nullEverything.toString()).contains("userId=<none>");
        assertThat(nullEverything.toString()).contains("tenantId=<none>");
        assertThat(nullEverything.toString()).contains("role=<none>");
        assertThat(nullEverything.toString()).contains("scopes=0");

        VaultPrincipal shortId = new VaultPrincipal("ab", "t", "USER", List.of());
        assertThat(shortId.toString()).contains("userId=***");
        assertThat(shortId.toString()).doesNotContain("ab");
    }
}
