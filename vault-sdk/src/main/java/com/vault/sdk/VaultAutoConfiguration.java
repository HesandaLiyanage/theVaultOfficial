package com.vault.sdk;

import com.vault.sdk.apikey.ApiKeyService;
import com.vault.sdk.apikey.ApiKeyRepository;
import com.vault.sdk.apikey.ApiKeyController;
import com.vault.sdk.audit.AuditAspect;
import com.vault.sdk.audit.AuditController;
import com.vault.sdk.audit.AuditLogRepository;
import com.vault.sdk.audit.AuditService;
import com.vault.sdk.auth.AuthController;
import com.vault.sdk.auth.AuthService;
import com.vault.sdk.auth.JwtService;
import com.vault.sdk.auth.TokenBlacklistService;
import com.vault.sdk.ratelimit.RateLimitService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@EnableConfigurationProperties(VaultProperties.class)
@EnableJpaRepositories(basePackages = "com.vault.sdk")
@EntityScan(basePackages = "com.vault.sdk")
@EnableMethodSecurity
@EnableAsync
@ConditionalOnProperty(prefix = "vault.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VaultAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder vaultPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultSecurityContext vaultSecurityContext() {
        return new VaultSecurityContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistService tokenBlacklistService(StringRedisTemplate redisTemplate) {
        return new TokenBlacklistService(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(VaultProperties properties, TokenBlacklistService tokenBlacklistService) {
        return new JwtService(properties, tokenBlacklistService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthService authService(
            PasswordEncoder passwordEncoder,
            VaultUserRepository vaultUserRepository,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService
    ) {
        return new AuthService(passwordEncoder, vaultUserRepository, jwtService, tokenBlacklistService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthController authController(AuthService authService) {
        return new AuthController(authService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitService rateLimitService(StringRedisTemplate redisTemplate, VaultProperties properties) {
        return new RateLimitService(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyService apiKeyService(
            ApiKeyRepository apiKeyRepository,
            PasswordEncoder passwordEncoder,
            RateLimitService rateLimitService
    ) {
        return new ApiKeyService(apiKeyRepository, passwordEncoder, rateLimitService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyController apiKeyController(ApiKeyService apiKeyService) {
        return new ApiKeyController(apiKeyService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditLogRepository auditLogRepository) {
        return new AuditService(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditService auditService) {
        return new AuditAspect(auditService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditController auditController(AuditLogRepository auditLogRepository) {
        return new AuditController(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultAuthFilter vaultAuthFilter(
            JwtService jwtService,
            ApiKeyService apiKeyService,
            VaultSecurityContext vaultSecurityContext,
            VaultProperties properties
    ) {
        return new VaultAuthFilter(jwtService, apiKeyService, vaultSecurityContext, properties.getPublicPaths());
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<VaultAuthFilter> vaultAuthFilterRegistration(VaultAuthFilter vaultAuthFilter) {
        FilterRegistrationBean<VaultAuthFilter> registration = new FilterRegistrationBean<>(vaultAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain vaultSecurityFilterChain(
            HttpSecurity http,
            VaultAuthFilter vaultAuthFilter,
            VaultProperties properties
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    for (String publicPath : properties.getPublicPaths()) {
                        auth.requestMatchers(publicPath).permitAll();
                    }
                    auth.requestMatchers("/error").permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(vaultAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
