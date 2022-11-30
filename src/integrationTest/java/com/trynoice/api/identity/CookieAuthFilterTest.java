package com.trynoice.api.identity;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import jakarta.transaction.Transactional;
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

import java.util.List;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.JwtType;
import static com.trynoice.api.testing.AuthTestUtils.assertValidJWT;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static com.trynoice.api.testing.AuthTestUtils.createSignedRefreshJwt;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CookieAuthFilterTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @MethodSource("authTestCases")
    void doFilter(
        JwtType refreshTokenType,
        JwtType accessTokenType,
        String requestPath,
        int responseStatus,
        boolean isExpectingNewCookies
    ) throws Exception {
        val authUser = createAuthUser(entityManager);
        var refreshToken = createSignedRefreshJwt(entityManager, hmacSecret, authUser, refreshTokenType);
        var accessToken = createSignedAccessJwt(hmacSecret, authUser, accessTokenType);
        val requestBuilder = get(requestPath);
        if (refreshToken != null) {
            requestBuilder.cookie(new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, refreshToken));
        }

        if (accessToken != null) {
            requestBuilder.cookie(new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, accessToken));
        }

        val result = mockMvc.perform(requestBuilder)
            .andExpect(status().is(responseStatus))
            .andReturn();

        List.of(CookieAuthFilter.REFRESH_TOKEN_COOKIE, CookieAuthFilter.ACCESS_TOKEN_COOKIE)
            .forEach((cookieName) -> {
                val cookie = result.getResponse().getCookie(cookieName);
                if (isExpectingNewCookies) {
                    assertNotNull(cookie);
                    assertValidJWT(hmacSecret, cookie.getValue());
                } else {
                    assertNull(cookie);
                }
            });
    }

    static Stream<Arguments> authTestCases() {
        return Stream.of(
            // refresh token type, access token type, path, response status, isExpectingNewCookies

            // /v1/* routes require authentication.
            arguments(JwtType.NULL, JwtType.NULL, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value(), false),
            arguments(JwtType.EMPTY, JwtType.EMPTY, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value(), false),
            arguments(JwtType.INVALID, JwtType.INVALID, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value(), false),
            arguments(JwtType.INVALID, JwtType.VALID, "/v1/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.VALID, JwtType.INVALID, "/v1/non-existing", HttpStatus.NOT_FOUND.value(), true),
            arguments(JwtType.EXPIRED, JwtType.EXPIRED, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value(), false),
            arguments(JwtType.EXPIRED, JwtType.VALID, "/v1/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.VALID, JwtType.EXPIRED, "/v1/non-existing", HttpStatus.NOT_FOUND.value(), true),
            arguments(JwtType.REUSED, JwtType.EXPIRED, "/v1/non-existing", HttpStatus.UNAUTHORIZED.value(), false),
            arguments(JwtType.VALID, JwtType.VALID, "/v1/non-existing", HttpStatus.NOT_FOUND.value(), false),

            // all other routes are publicly accessible.
            arguments(JwtType.NULL, JwtType.NULL, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.EMPTY, JwtType.EMPTY, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.INVALID, JwtType.INVALID, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.INVALID, JwtType.VALID, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.VALID, JwtType.INVALID, "/non-existing", HttpStatus.NOT_FOUND.value(), true),
            arguments(JwtType.EXPIRED, JwtType.EXPIRED, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.EXPIRED, JwtType.VALID, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.VALID, JwtType.EXPIRED, "/non-existing", HttpStatus.NOT_FOUND.value(), true),
            arguments(JwtType.REUSED, JwtType.EXPIRED, "/non-existing", HttpStatus.NOT_FOUND.value(), false),
            arguments(JwtType.VALID, JwtType.VALID, "/non-existing", HttpStatus.NOT_FOUND.value(), false)
        );
    }
}
