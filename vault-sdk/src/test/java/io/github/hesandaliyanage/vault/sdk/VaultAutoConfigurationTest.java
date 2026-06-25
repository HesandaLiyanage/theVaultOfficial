package io.github.hesandaliyanage.vault.sdk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class VaultAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VaultAutoConfiguration.class));

    @Test
    void autoConfigDisabledWhenBaseUrlAbsent() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(TokenValidator.class);
            assertThat(ctx).doesNotHaveBean(VaultAuthFilter.class);
        });
    }

    @Test
    void autoConfigWiresFilterWhenBaseUrlSet() {
        runner.withPropertyValues(
                "vault.client.base-url=https://vault.test",
                "vault.client.service-key=k"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(VaultAuthFilter.class);
            assertThat(ctx).hasSingleBean(TokenValidator.class);
            assertThat(ctx).doesNotHaveBean(VaultAuditClient.class);
            assertThat(ctx.getBean(TokenValidator.class)).isInstanceOf(VaultClient.class);
        });
    }

    @Test
    void cachingValidatorIsWiredWhenCacheEnabled() {
        runner.withPropertyValues(
                "vault.client.base-url=https://vault.test",
                "vault.client.service-key=k",
                "vault.client.cache.enabled=true"
        ).run(ctx -> {
            assertThat(ctx.getBean(TokenValidator.class)).isInstanceOf(CachingVaultClient.class);
        });
    }

    @Test
    void jwksValidatorIsWiredWhenJwksUriSet() {
        runner.withPropertyValues(
                "vault.client.base-url=https://vault.test",
                "vault.client.service-key=k",
                "vault.client.jwks.uri=https://vault.test/.well-known/jwks.json"
        ).run(ctx -> {
            assertThat(ctx.getBean(TokenValidator.class)).isInstanceOf(JwksTokenValidator.class);
        });
    }

    @Test
    void auditClientIsWiredWhenEnabled() {
        runner.withPropertyValues(
                "vault.client.base-url=https://vault.test",
                "vault.client.service-key=k",
                "vault.audit.enabled=true"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(VaultAuditClient.class);
        });
    }

    @Test
    void plainHttpBaseUrlIsRejectedByDefault() {
        runner.withPropertyValues(
                "vault.client.base-url=http://vault.test",
                "vault.client.service-key=k"
        ).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vault.client.base-url")
                    .hasMessageContaining("https");
        });
    }

    @Test
    void plainHttpBaseUrlIsAcceptedWhenAllowInsecureHttpEnabled() {
        runner.withPropertyValues(
                "vault.client.base-url=http://vault.test",
                "vault.client.service-key=k",
                "vault.client.allow-insecure-http=true"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(VaultAuthFilter.class);
        });
    }

    @Test
    void plainHttpJwksUriIsRejectedByDefault() {
        runner.withPropertyValues(
                "vault.client.base-url=https://vault.test",
                "vault.client.service-key=k",
                "vault.client.jwks.uri=http://vault.test/.well-known/jwks.json"
        ).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vault.client.jwks.uri");
        });
    }
}
