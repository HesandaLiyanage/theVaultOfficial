package com.hess.thevault.internal.dto;

import java.util.List;

public record InternalValidateResponse(
        boolean valid,
        String userId,
        String tenantId,
        String role,
        List<String> scopes,
        String reason
) {

    public static InternalValidateResponse success(
            String userId,
            String tenantId,
            String role,
            List<String> scopes
    ) {
        return new InternalValidateResponse(true, userId, tenantId, role, scopes, null);
    }

    public static InternalValidateResponse failure(String reason) {
        return new InternalValidateResponse(false, null, null, null, List.of(), reason);
    }
}
