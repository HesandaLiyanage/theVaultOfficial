package com.hess.thevault.internal;

import com.hess.thevault.apikey.ApiKeyService;
import com.hess.thevault.audit.AuditRecord;
import com.hess.thevault.audit.AuditService;
import com.hess.thevault.auth.JwtService;
import io.github.hesandaliyanage.vault.protocol.AuditRequest;
import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateRequest;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private final String internalServiceKey;
    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final AuditService auditService;

    public InternalController(
            @Value("${vault.internal-service-key}") String internalServiceKey,
            JwtService jwtService,
            ApiKeyService apiKeyService,
            AuditService auditService
    ) {
        this.internalServiceKey = internalServiceKey;
        this.jwtService = jwtService;
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
    }

    @PostMapping("/validate")
    public ValidateResponse validate(
            @RequestHeader(value = SERVICE_KEY_HEADER, required = false) String serviceKey,
            @Valid @RequestBody ValidateRequest request
    ) {
        requireServiceKey(serviceKey);

        if (request.type() == TokenType.JWT) {
            return validateJwt(request.token());
        }
        if (request.type() == TokenType.API_KEY) {
            return validateApiKey(request.token());
        }

        return ValidateResponse.failure("Unsupported token type");
    }

    @PostMapping("/audit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void audit(
            @RequestHeader(value = SERVICE_KEY_HEADER, required = false) String serviceKey,
            @Valid @RequestBody AuditRequest request,
            HttpServletRequest servletRequest
    ) {
        requireServiceKey(serviceKey);

        auditService.record(new AuditRecord(
                request.tenantId(),
                request.userId(),
                request.action(),
                request.resource(),
                ipAddress(servletRequest),
                request.status() == null || request.status().isBlank() ? "SUCCESS" : request.status(),
                "{\"source\":\"vault-sdk\"}"
        ));
    }

    private ValidateResponse validateJwt(String token) {
        if (!jwtService.isTokenValid(token, JwtService.TOKEN_TYPE_ACCESS)) {
            return ValidateResponse.failure("Invalid JWT");
        }

        return ValidateResponse.success(
                jwtService.extractVaultId(token),
                jwtService.extractTenantId(token),
                jwtService.extractRole(token),
                List.of()
        );
    }

    private ValidateResponse validateApiKey(String rawKey) {
        var result = apiKeyService.validateRawKey(rawKey);
        if (!result.valid()) {
            return ValidateResponse.failure(result.reason());
        }

        return ValidateResponse.success(
                result.createdBy(),
                result.tenantId(),
                "API_KEY",
                result.scopes()
        );
    }

    private void requireServiceKey(String providedKey) {
        if (!constantTimeEquals(internalServiceKey, providedKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service key");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String ipAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
