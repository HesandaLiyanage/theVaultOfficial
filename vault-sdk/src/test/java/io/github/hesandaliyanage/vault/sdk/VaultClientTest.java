package io.github.hesandaliyanage.vault.sdk;

import io.github.hesandaliyanage.vault.protocol.TokenType;
import io.github.hesandaliyanage.vault.protocol.ValidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class VaultClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private VaultClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://vault.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VaultClient(builder.build(), "secret-key");
    }

    @Test
    void validate_sendsServiceKeyHeaderAndReturnsSuccess() {
        server.expect(requestTo("http://vault.test/internal/validate"))
                .andExpect(method(POST))
                .andExpect(header("X-Service-Key", "secret-key"))
                .andExpect(jsonPath("$.token").value("abc"))
                .andExpect(jsonPath("$.type").value("JWT"))
                .andRespond(withSuccess(
                        "{\"valid\":true,\"userId\":\"u1\",\"tenantId\":\"t1\",\"role\":\"USER\",\"scopes\":[],\"reason\":null}",
                        MediaType.APPLICATION_JSON
                ));

        ValidateResponse response = client.validate("abc", TokenType.JWT);

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo("u1");
        assertThat(response.tenantId()).isEqualTo("t1");
        assertThat(response.role()).isEqualTo("USER");
        server.verify();
    }

    @Test
    void validate_returnsFailureWhenServerReturns5xx() {
        server.expect(requestTo("http://vault.test/internal/validate"))
                .andRespond(withServerError());

        ValidateResponse response = client.validate("abc", TokenType.JWT);

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("500");
    }

    @Test
    void validate_returnsFailureWhenServerRejectsServiceKey() {
        server.expect(requestTo("http://vault.test/internal/validate"))
                .andRespond(request -> {
                    var mockResponse = new org.springframework.mock.http.client.MockClientHttpResponse(
                            new byte[0],
                            HttpStatus.UNAUTHORIZED
                    );
                    return mockResponse;
                });

        ValidateResponse response = client.validate("abc", TokenType.JWT);

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("401");
    }
}
