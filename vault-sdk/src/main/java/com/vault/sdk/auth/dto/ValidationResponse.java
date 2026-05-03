package com.vault.sdk.auth.dto;

public record ValidationResponse(boolean valid, String reason) {

    public static ValidationResponse success() {
        return new ValidationResponse(true, null);
    }

    public static ValidationResponse failure(String reason) {
        return new ValidationResponse(false, reason);
    }
}
