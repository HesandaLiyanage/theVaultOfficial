package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateRequest;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client that asks a vault-server to validate a JWT or API key.
 *
 * <p>The client is stateless and thread-safe; one instance per application
 * is fine. Errors talking to the server (timeouts, 5xx) are surfaced as
 * {@link ValidateResponse#failure(String) failure} responses rather than
 * exceptions so the auth filter can map them to a 401 without special-casing
 * transport problems.
 */
public class VaultClient {

    private static final Logger log = LoggerFactory.getLogger(VaultClient.class);
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALIDATE_PATH = "/internal/validate";

    private final RestClient restClient;
    private final String serviceKey;

    public VaultClient(RestClient restClient, String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    public ValidateResponse validate(String token, TokenType type) {
        try {
            return restClient.post()
                    .uri(VALIDATE_PATH)
                    .header(SERVICE_KEY_HEADER, serviceKey)
                    .body(new ValidateRequest(token, type))
                    .retrieve()
                    .body(ValidateResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("vault-server rejected validation request: {} {}", e.getStatusCode(), e.getStatusText());
            return ValidateResponse.failure("vault-server returned " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("vault-server unreachable: {}", e.getMessage());
            return ValidateResponse.failure("vault-server unreachable");
        }
    }
}
