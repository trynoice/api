package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.models.RefreshToken;
import com.trynoice.api.identity.models.SignInRequest;
import com.trynoice.api.identity.models.SignUpRequest;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignInTokenDispatchStrategy signInTokenDispatchStrategy;

    @ParameterizedTest(name = "{displayName} - email={0} name={1} responseStatus={2} expectingSignInTokenDispatch={3}")
    @MethodSource("signUpTestCases")
    void signUp(String email, String name, int expectedResponseStatus, boolean isExpectingSignInTokenDispatch) throws Exception {
        doNothing()
            .when(signInTokenDispatchStrategy)
            .dispatch(any(), any());

        doThrow(new SignInTokenDispatchException("test-error", null))
            .when(signInTokenDispatchStrategy)
            .dispatch(any(), eq("test-5@test.org"));

        mockMvc.perform(
                post("/v1/accounts/signUp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignUpRequest(email, name))))
            .andExpect(status().is(expectedResponseStatus));

        val tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(isExpectingSignInTokenDispatch ? 1 : 0))
            .dispatch(tokenCaptor.capture(), eq(email));

        if (expectedResponseStatus == HttpStatus.CREATED.value()) {
            assertValidJWT(tokenCaptor.getValue());
        }
    }

    static Stream<Arguments> signUpTestCases() {
        return Stream.of(
            // email, name, expectedResponseStatus, isExpectingSignInTokenDispatch
            arguments(null, null, HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("", "test-name-1", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("test-2@test.org", "", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("not-an-email", "", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("test-4@test.org", "test-name-4", HttpStatus.CREATED.value(), true),
            arguments("test-4@test.org", "test-name-4", HttpStatus.CREATED.value(), true), // repeated input
            arguments("test-5@test.org", "test-name-5", HttpStatus.INTERNAL_SERVER_ERROR.value(), true)
        );
    }

    @ParameterizedTest(name = "{displayName} - email={0} responseStatus={1} expectingSignInTokenDispatch={2}")
    @MethodSource("signInTestCases")
    void signIn(String email, int expectedResponseStatus, boolean isExpectingSignInTokenDispatch) throws Exception {
        // create auth user for test-cases that expect it.
        if (email != null && email.startsWith("existing-")) {
            entityManager.persist(
                AuthUser.builder()
                    .email(email)
                    .name("test-name")
                    .build());
        }

        doNothing()
            .when(signInTokenDispatchStrategy)
            .dispatch(any(), any());

        doThrow(new SignInTokenDispatchException("test-error", null))
            .when(signInTokenDispatchStrategy)
            .dispatch(any(), eq("existing-1@test.org"));

        mockMvc.perform(
                post("/v1/accounts/signIn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignInRequest(email))))
            .andExpect(status().is(expectedResponseStatus));

        val tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(isExpectingSignInTokenDispatch ? 1 : 0))
            .dispatch(tokenCaptor.capture(), eq(email));

        if (expectedResponseStatus == HttpStatus.CREATED.value()) {
            assertValidJWT(tokenCaptor.getValue());
        }
    }

    static Stream<Arguments> signInTestCases() {
        return Stream.of(
            // email, expectedResponseStatus, isExpectingSignInTokenDispatch
            arguments(null, HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("not-an-email", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("non-existing@test.org", HttpStatus.NOT_FOUND.value(), false),
            arguments("existing-0@test.org", HttpStatus.CREATED.value(), true),
            arguments("existing-1@test.org", HttpStatus.INTERNAL_SERVER_ERROR.value(), true)
        );
    }

    private void assertValidJWT(String token) {
        assertNotNull(token);
        assertDoesNotThrow(() ->
            JWT.require(Algorithm.HMAC256(hmacSecret))
                .build()
                .verify(token));
    }

    @ParameterizedTest(name = "{displayName} - tokenType={0} userAgent={1} responseStatus={2}")
    @MethodSource("issueCredentialsTestCases")
    void issueCredentials(String tokenType, String userAgent, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val token = createRefreshToken(tokenType);
        val result = mockMvc.perform(
                get("/v1/accounts/credentials")
                    .header("X-Refresh-Token", token)
                    .header("User-Agent", userAgent))
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val authCredentials = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(), AuthCredentials.class);

            assertValidJWT(authCredentials.getRefreshToken());
            assertValidJWT(authCredentials.getAccessToken());
        }
    }

    static Stream<Arguments> issueCredentialsTestCases() {
        return Stream.of(
            // tokenType, userAgent, expectedResponseStatus
            arguments("", "test-user-agent", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments("valid-token", "", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments("invalid-token", "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments("expired-token", "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments("reused-token", "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments("valid-token", "test-user-agent", HttpStatus.OK.value())
        );
    }

    private String createRefreshToken(@NonNull String type) {
        var expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        var jwtVersion = 0L;
        switch (type) {
            case "valid-token":
                break;
            case "expired-token":
                expiresAt = LocalDateTime.now().minus(Duration.ofHours(1));
                break;
            case "reused-token":
                jwtVersion = 10L;
                break;
            default: // by default return type as the token
                return type;
        }

        val uuid = UUID.randomUUID().toString();
        val authUser = AuthUser.builder()
            .name(uuid)
            .email(uuid + "@test.org")
            .build();

        val refreshToken = RefreshToken.builder()
            .expiresAt(expiresAt)
            .userAgent("")
            .owner(authUser)
            .build();

        entityManager.persist(authUser);
        entityManager.persist(refreshToken);

        return JWT.create()
            .withJWTId("" + refreshToken.getId())
            .withClaim(AccountService.REFRESH_TOKEN_ORDINAL_CLAIM, Objects.requireNonNullElse(jwtVersion, refreshToken.getVersion()))
            .withExpiresAt(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
            .sign(Algorithm.HMAC256(hmacSecret));
    }
}
