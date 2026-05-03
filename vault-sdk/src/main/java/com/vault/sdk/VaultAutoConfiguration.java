package com.vault.sdk;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(VaultProperties.class)
@ConditionalOnProperty(prefix = "vault.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VaultAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultClient vaultClient(VaultProperties properties) {
        return new VaultClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultSecurityContext vaultSecurityContext() {
        return new VaultSecurityContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultAuthFilter vaultAuthFilter(VaultClient vaultClient, VaultSecurityContext vaultSecurityContext) {
        return new VaultAuthFilter(vaultClient, vaultSecurityContext, vaultClient.getProperties().getPublicPaths());
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<VaultAuthFilter> vaultAuthFilterRegistration(VaultAuthFilter vaultAuthFilter) {
        FilterRegistrationBean<VaultAuthFilter> registration = new FilterRegistrationBean<>(vaultAuthFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
