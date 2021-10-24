package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Calendar;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class BearerTokenAuthFilterTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{displayName} - tokenType={0} requestPath={1} responseStatus={2}")
    @MethodSource("authTestCases")
    void auth(String tokenType, String requestPath, int responseStatus) throws Exception {
        var token = createAccessToken(tokenType);
        val requestBuilder = get(requestPath);
        if (token != null) {
            requestBuilder.header("Authorization", "bearer " + token);
        }

        mockMvc.perform(requestBuilder).andExpect(status().is(responseStatus));
    }

    static Stream<Arguments> authTestCases() {
        return Stream.of(
            // access token type, path, response status

            // /v1/accounts/* routes are publicly accessible, using refresh token auth where needed.
            arguments(null, "/v1/accounts/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("", "/v1/accounts/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("invalid-token", "/v1/accounts/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("expired-token", "/v1/accounts/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("valid-token", "/v1/accounts/non-existing", HttpStatus.NOT_FOUND.value()),

            // /v1/* routes require authentication.
            arguments(null, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments("", "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments("invalid-token", "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments("expired-token", "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments("valid-token", "/v1/non-existing", HttpStatus.NOT_FOUND.value()),

            // all other routes are publicly accessible.
            arguments(null, "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("", "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("invalid-token", "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("expired-token", "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments("valid-token", "/non-existing", HttpStatus.NOT_FOUND.value())
        );
    }

    private String createAccessToken(String tokenType) {
        val expiresAt = Calendar.getInstance();
        switch (requireNonNullElse(tokenType, "")) {
            case "valid-token":
                expiresAt.add(Calendar.HOUR, 1);
                break;
            case "expired-token":
                expiresAt.add(Calendar.HOUR, -1);
                break;
            default:
                return tokenType;  // return type as token by default
        }

        return JWT.create()
            .withSubject("1")
            .withExpiresAt(expiresAt.getTime())
            .sign(Algorithm.HMAC256(hmacSecret));
    }
}
