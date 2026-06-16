package io.github.hesandaliyanage.vault.sdk;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Drop-in auto-configuration for the Vault SDK.
 *
 * <p>Activates only when {@code vault.client.base-url} is set, so apps that
 * pull the SDK in transitively without configuring it stay completely
 * untouched. Wires:
 *
 * <ul>
 *   <li>A {@link RestClient} pointed at {@code vault.client.base-url}.</li>
 *   <li>A {@link VaultClient} for remote validation.</li>
 *   <li>A {@link CachingVaultClient} on top of it when
 *       {@code vault.client.cache.enabled=true}.</li>
 *   <li>A {@link JwksTokenValidator} on top of either, when
 *       {@code vault.client.jwks.uri} is set.</li>
 *   <li>A {@link VaultAuditClient} when
 *       {@code vault.audit.enabled=true}.</li>
 *   <li>A {@link VaultAuthFilter} ready to be added to the security chain.</li>
 * </ul>
 *
 * <p>Each bean is gated by {@link ConditionalOnMissingBean} so consumers can
 * override any individual piece without forking the whole configuration.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "vault.client", name = "base-url")
@EnableConfigurationProperties({
        VaultClientProperties.class,
        VaultCacheProperties.class,
        VaultAuditProperties.class,
        VaultFilterProperties.class,
        JwksProperties.class
})
public class VaultAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "vaultRestClient")
    public RestClient vaultRestClient(VaultClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "vaultTokenValidator")
    public TokenValidator vaultTokenValidator(
            RestClient vaultRestClient,
            VaultClientProperties clientProperties,
            VaultCacheProperties cacheProperties,
            JwksProperties jwksProperties
    ) {
        TokenValidator validator = new VaultClient(vaultRestClient, clientProperties.serviceKey());
        if (cacheProperties.enabled()) {
            validator = new CachingVaultClient(validator, cacheProperties);
        }
        if (jwksProperties.uri() != null) {
            validator = new JwksTokenValidator(
                    jwksProperties.uri(),
                    validator,
                    jwksProperties.skipRemoteRevocationCheck()
            );
        }
        return validator;
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultAuthFilter vaultAuthFilter(TokenValidator validator, VaultFilterProperties properties) {
        return new VaultAuthFilter(validator, properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vault.audit", name = "enabled", havingValue = "true")
    public VaultAuditClient vaultAuditClient(
            RestClient vaultRestClient,
            VaultClientProperties clientProperties,
            VaultAuditProperties auditProperties
    ) {
        return new VaultAuditClient(vaultRestClient, clientProperties.serviceKey(), auditProperties);
    }

}
