package com.hess.thevault.audit;

import com.hess.thevault.auth.dto.AuthResponse;
import com.hess.thevault.auth.VaultUser;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            auditService.record(record(audited, "SUCCESS", result, null));
            return result;
        } catch (Throwable ex) {
            auditService.record(record(audited, "FAILURE", null, ex.getClass().getSimpleName()));
            throw ex;
        }
    }

    private AuditRecord record(Audited audited, String status, Object result, String error) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;
        String tenantId = null;

        if (authentication != null && authentication.getPrincipal() instanceof VaultUser user) {
            userId = user.getVaultId();
            tenantId = user.getTenantId();
        }

        if (result instanceof AuthResponse authResponse) {
            userId = authResponse.userId();
            tenantId = authResponse.tenantId();
        }

        String metadata = error == null
                ? "{\"source\":\"vault-server\"}"
                : "{\"source\":\"vault-server\",\"error\":\"%s\"}".formatted(error);

        return new AuditRecord(
                tenantId,
                userId,
                audited.action(),
                resource(audited),
                ipAddress(),
                status,
                metadata
        );
    }

    private String resource(Audited audited) {
        if (!audited.resource().isBlank()) {
            return audited.resource();
        }
        HttpServletRequest request = request();
        return request == null ? null : request.getRequestURI();
    }

    private String ipAddress() {
        HttpServletRequest request = request();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
