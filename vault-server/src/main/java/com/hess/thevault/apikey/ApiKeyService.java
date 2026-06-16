package com.hess.thevault.apikey;

import com.hess.thevault.apikey.dto.ApiKeyResponse;
import com.hess.thevault.apikey.dto.GenerateApiKeyRequest;
import com.hess.thevault.apikey.dto.GenerateApiKeyResponse;
import com.hess.thevault.audit.Audited;
import com.hess.thevault.ratelimit.RateLimitResult;
import com.hess.thevault.ratelimit.RateLimitService;
import com.hess.thevault.auth.VaultUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "vault_";
    private static final int RANDOM_BYTES = 32;
    private static final int STORED_PREFIX_LENGTH = 10;

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            PasswordEncoder passwordEncoder,
            RateLimitService rateLimitService
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
    }

    @Audited(action = "API_KEY_CREATED", resource = "/api-keys")
    @Transactional
    public GenerateApiKeyResponse generate(GenerateApiKeyRequest request, VaultUser creator) {
        List<String> scopes = normalizeScopes(request.scopes());
        LocalDateTime expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key expiration must be in the future");
        }

        String rawKey = generateRawKey();
        ApiKey apiKey = new ApiKey(
                passwordEncoder.encode(rawKey),
                keyPrefix(rawKey),
                request.name().trim(),
                creator.getTenantId(),
                creator.getVaultId(),
                String.join(",", scopes),
                expiresAt
        );

        ApiKey saved = apiKeyRepository.save(apiKey);
        return new GenerateApiKeyResponse(
                saved.getId(),
                rawKey,
                saved.getKeyPrefix(),
                saved.getName(),
                saved.getTenantId(),
                scopes,
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForUser(VaultUser user) {
        return apiKeyRepository
                .findByTenantIdAndCreatedByAndRevokedFalseOrderByCreatedAtDesc(user.getTenantId(), user.getVaultId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForTenant(VaultUser user) {
        return apiKeyRepository
                .findByTenantIdAndRevokedFalseOrderByCreatedAtDesc(user.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Audited(action = "API_KEY_REVOKED", resource = "/api-keys")
    @Transactional
    public void revoke(UUID keyId, VaultUser requestingUser) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        boolean sameTenant = apiKey.getTenantId().equals(requestingUser.getTenantId());
        boolean owner = apiKey.getCreatedBy().equals(requestingUser.getVaultId());
        boolean admin = "ADMIN".equals(requestingUser.getRole()) || "TENANT_ADMIN".equals(requestingUser.getRole());

        if (!sameTenant || (!owner && !admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot revoke this API key");
        }

        apiKey.revoke();
    }

    @Transactional
    public ApiKeyValidationResult validateRawKey(String rawKey) {
        if (!StringUtils.hasText(rawKey) || !rawKey.startsWith(KEY_PREFIX)) {
            return ApiKeyValidationResult.failure("Invalid API key");
        }

        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefix(keyPrefix(rawKey));
        ApiKey apiKey = candidates.stream()
                .filter(candidate -> passwordEncoder.matches(rawKey, candidate.getKeyHash()))
                .min(Comparator.comparing(ApiKey::getCreatedAt))
                .orElse(null);

        if (apiKey == null) {
            return ApiKeyValidationResult.failure("Invalid API key");
        }
        if (apiKey.isRevoked()) {
            return ApiKeyValidationResult.failure("API key is revoked");
        }
        if (apiKey.isExpired(LocalDateTime.now())) {
            return ApiKeyValidationResult.failure("API key is expired");
        }

        RateLimitResult rateLimit = rateLimitService.consume(apiKey);
        if (!rateLimit.allowed()) {
            return ApiKeyValidationResult.failure("Rate limit exceeded");
        }

        apiKey.markUsed(LocalDateTime.now());
        return ApiKeyValidationResult.success(apiKey, parseScopes(apiKey.getScopes()));
    }

    private GenerateApiKeyResponse toGenerateResponse(ApiKey apiKey, String rawKey) {
        return new GenerateApiKeyResponse(
                apiKey.getId(),
                rawKey,
                apiKey.getKeyPrefix(),
                apiKey.getName(),
                apiKey.getTenantId(),
                parseScopes(apiKey.getScopes()),
                apiKey.getExpiresAt(),
                apiKey.getCreatedAt()
        );
    }

    private ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.getName(),
                apiKey.getTenantId(),
                apiKey.getCreatedBy(),
                parseScopes(apiKey.getScopes()),
                apiKey.getExpiresAt(),
                apiKey.getLastUsedAt(),
                apiKey.isRevoked(),
                apiKey.getCreatedAt()
        );
    }

    private List<String> normalizeScopes(List<String> scopes) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            normalized.add(ApiScope.from(scope).name());
        }
        return List.copyOf(normalized);
    }

    private List<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return List.of();
        }
        return List.of(scopes.split(","));
    }

    private String generateRawKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String keyPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(STORED_PREFIX_LENGTH, rawKey.length()));
    }
}
