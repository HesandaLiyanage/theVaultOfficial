package com.hess.thevault.apikey;

import java.util.Arrays;

public enum ApiScope {
    READ,
    WRITE,
    DELETE;

    public static ApiScope from(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported API key scope: " + value));
    }
}
