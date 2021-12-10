package com.trynoice.api.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.viewmodels.AuthCredentialsResponse;
import com.trynoice.api.identity.viewmodels.ProfileResponse;
import com.trynoice.api.identity.viewmodels.SignInRequest;
import com.trynoice.api.identity.viewmodels.SignUpRequest;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.persistence.EntityManager;
import javax.servlet.http.Cookie;
import javax.transaction.Transactional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.JwtType;
import static com.trynoice.api.testing.AuthTestUtils.assertValidJWT;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createRefreshToken;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static com.trynoice.api.testing.AuthTestUtils.createSignedRefreshJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
            assertValidJWT(hmacSecret, tokenCaptor.getValue());
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
        if ("existing".equals(email)) {
            val authUser = createAuthUser(entityManager);
            email = authUser.getEmail();
        }

        doNothing().when(signInTokenDispatchStrategy).dispatch(any(), any());
        mockMvc.perform(
                post("/v1/accounts/signIn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignInRequest(email))))
            .andExpect(status().is(expectedResponseStatus));

        val tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(isExpectingSignInTokenDispatch ? 1 : 0))
            .dispatch(tokenCaptor.capture(), eq(email));

        if (expectedResponseStatus == HttpStatus.CREATED.value()) {
            assertValidJWT(hmacSecret, tokenCaptor.getValue());
        }
    }

    static Stream<Arguments> signInTestCases() {
        return Stream.of(
            // email, expectedResponseStatus, isExpectingSignInTokenDispatch
            arguments(null, HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("not-an-email", HttpStatus.UNPROCESSABLE_ENTITY.value(), false),
            arguments("non-existing@test.org", HttpStatus.NOT_FOUND.value(), false),
            arguments("existing", HttpStatus.CREATED.value(), true)
        );
    }

    @ParameterizedTest(name = "{displayName} - tokenType={0} userAgent={1} responseStatus={2}")
    @MethodSource("signOutTestCases")
    void signOut_withHeader(JwtType tokenType, String userAgent, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        val token = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
        mockMvc.perform(
                get("/v1/accounts/signOut")
                    .header(AccountController.USER_AGENT_HEADER, userAgent)
                    .header(AccountController.REFRESH_TOKEN_HEADER, token))
            .andExpect(status().is(expectedResponseStatus));
    }

    @ParameterizedTest(name = "{displayName} - tokenType={0} userAgent={1} responseStatus={2}")
    @MethodSource("signOutTestCases")
    void signOut_withCookie(JwtType tokenType, String userAgent, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        val token = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
        mockMvc.perform(
                get("/v1/accounts/signOut")
                    .header(AccountController.USER_AGENT_HEADER, userAgent)
                    .cookie(new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, token)))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> signOutTestCases() {
        return Stream.of(
            // tokenType, userAgent, expectedResponseStatus
            arguments(JwtType.EMPTY, "test-user-agent", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(JwtType.VALID, "", HttpStatus.OK.value()),
            arguments(JwtType.INVALID, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.REUSED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, "test-user-agent", HttpStatus.OK.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - tokenType={0} userAgent={1} responseStatus={2}")
    @MethodSource("issueCredentialsTestCases")
    void issueCredentials(JwtType tokenType, String userAgent, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        val token = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
        val result = mockMvc.perform(
                get("/v1/accounts/credentials")
                    .header(AccountController.REFRESH_TOKEN_HEADER, token)
                    .header(AccountController.USER_AGENT_HEADER, userAgent))
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val authCredentials = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(), AuthCredentialsResponse.class);

            assertValidJWT(hmacSecret, authCredentials.getRefreshToken());
            assertValidJWT(hmacSecret, authCredentials.getAccessToken());

            entityManager.refresh(authUser);
            assertEquals((short) 0, authUser.getSignInAttempts());
        }
    }

    static Stream<Arguments> issueCredentialsTestCases() {
        return Stream.of(
            // tokenType, userAgent, expectedResponseStatus
            arguments(JwtType.EMPTY, "test-user-agent", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(JwtType.VALID, "", HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(JwtType.INVALID, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.REUSED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, "test-user-agent", HttpStatus.OK.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - signInAttempts={0} responseStatus={1}")
    @MethodSource("emailBlacklistingTestCases")
    void emailBlacklisting(int signInAttempts, int expectedResponseStatus) throws Exception {
        val user = createAuthUser(entityManager);
        user.setSignInAttempts((short) signInAttempts);
        entityManager.persist(user);

        // perform sign-up
        mockMvc.perform(
                post("/v1/accounts/signUp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignUpRequest(user.getEmail(), user.getName()))))
            .andExpect(status().is(expectedResponseStatus));

        user.setSignInAttempts((short) signInAttempts);
        entityManager.persist(user);

        // perform sign-in
        mockMvc.perform(
                post("/v1/accounts/signIn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignInRequest(user.getEmail()))))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> emailBlacklistingTestCases() {
        return Stream.of(
            // signInAttempts, responseStatus
            arguments(0, HttpStatus.CREATED.value()),
            arguments(AccountService.MAX_SIGN_IN_ATTEMPTS_PER_USER / 2, HttpStatus.CREATED.value()),
            arguments(AccountService.MAX_SIGN_IN_ATTEMPTS_PER_USER, HttpStatus.FORBIDDEN.value()),
            arguments(AccountService.MAX_SIGN_IN_ATTEMPTS_PER_USER * 2, HttpStatus.FORBIDDEN.value())
        );
    }

    @Test
    void revokeRefreshToken() throws Exception {
        val authUser = createAuthUser(entityManager);
        val refreshToken = createRefreshToken(entityManager, authUser);
        val accessToken = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);

        final Function<Long, MockHttpServletRequestBuilder> requestBuilder =
            id -> delete("/v1/accounts/refreshTokens/" + id)
                .header("Authorization", "Bearer " + accessToken);

        // non-existing refresh token id
        mockMvc.perform(requestBuilder.apply(99999999L))
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

        // existing and owned refresh token id
        mockMvc.perform(requestBuilder.apply(refreshToken.getId()))
            .andExpect(status().is(HttpStatus.OK.value()));

        // existing but not owned refresh token id
        val anotherRefreshToken = createRefreshToken(entityManager, createAuthUser(entityManager));
        mockMvc.perform(requestBuilder.apply(anotherRefreshToken.getId()))
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void getProfile() throws Exception {
        val authUser = createAuthUser(entityManager);
        createRefreshToken(entityManager, authUser);
        val accessToken = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);
        val result = mockMvc.perform(
                get("/v1/accounts/profile")
                    .header("Authorization", "bearer " + accessToken))
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();

        val profile = objectMapper.readValue(result.getResponse().getContentAsByteArray(), ProfileResponse.class);
        assertEquals(authUser.getId(), profile.getAccountId());
        assertEquals(authUser.getName(), profile.getName());
        assertEquals(authUser.getEmail(), profile.getEmail());
        assertEquals(1, profile.getActiveSessions().size());
    }
}
