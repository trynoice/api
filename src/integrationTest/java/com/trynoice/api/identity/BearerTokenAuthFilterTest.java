package com.trynoice.api.identity;

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
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.JwtType;
import static com.trynoice.api.testing.AuthTestUtils.createAccessToken;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class BearerTokenAuthFilterTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{displayName} - tokenType={0} requestPath={1} responseStatus={2}")
    @MethodSource("authTestCases")
    void auth(JwtType tokenType, String requestPath, int responseStatus) throws Exception {
        val authUser = createAuthUser(entityManager);
        val token = createAccessToken(hmacSecret, authUser, tokenType);
        val requestBuilder = get(requestPath);
        if (token != null) {
            requestBuilder.header("Authorization", "bearer " + token);
        }

        mockMvc.perform(requestBuilder).andExpect(status().is(responseStatus));
    }

    static Stream<Arguments> authTestCases() {
        return Stream.of(
            // access token type, path, response status

            // /v1/* routes require authentication.
            arguments(JwtType.NULL, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EMPTY, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.INVALID, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, "/v1/non-existing", HttpStatus.NOT_FOUND.value()),

            // all other routes are publicly accessible.
            arguments(JwtType.NULL, "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments(JwtType.EMPTY, "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments(JwtType.INVALID, "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments(JwtType.EXPIRED, "/non-existing", HttpStatus.NOT_FOUND.value()),
            arguments(JwtType.VALID, "/non-existing", HttpStatus.NOT_FOUND.value())
        );
    }
}
