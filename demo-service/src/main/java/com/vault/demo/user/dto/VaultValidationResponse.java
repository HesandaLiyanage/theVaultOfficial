package com.vault.demo.user.dto;

public record VaultValidationResponse(boolean valid, String reason) {
}
