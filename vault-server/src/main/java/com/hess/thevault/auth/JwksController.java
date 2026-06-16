package com.hess.thevault.auth;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the server's RSA public key as a JSON Web Key Set. Consumers
 * running the SDK in JWKS local-verify mode hit this endpoint to verify
 * JWT signatures without calling /internal/validate on every request.
 *
 * <p>The endpoint is intentionally unauthenticated; the public key is, by
 * definition, public.
 */
@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey key = jwtService.getPublicKey();
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "kid", jwtService.getKeyId(),
                "alg", "RS256",
                "use", "sig",
                "n", base64Url(key.getModulus()),
                "e", base64Url(key.getPublicExponent())
        );
        return Map.of("keys", List.of(jwk));
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
