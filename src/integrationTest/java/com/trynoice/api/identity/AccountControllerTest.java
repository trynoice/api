package com.trynoice.api.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.payload.AuthCredentialsResponse;
import com.trynoice.api.identity.payload.SignInParams;
import com.trynoice.api.identity.payload.SignUpParams;
import com.trynoice.api.identity.payload.UpdateProfileParams;
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

import javax.persistence.EntityManager;
import javax.servlet.http.Cookie;
import javax.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.JwtType;
import static com.trynoice.api.testing.AuthTestUtils.assertValidJWT;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createRefreshToken;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static com.trynoice.api.testing.AuthTestUtils.createSignedRefreshJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @ParameterizedTest
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
                    .content(objectMapper.writeValueAsBytes(new SignUpParams(email, name))))
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
            arguments(null, null, HttpStatus.BAD_REQUEST.value(), false),
            arguments("", "test-name-1", HttpStatus.BAD_REQUEST.value(), false),
            arguments("test-2@test.org", "", HttpStatus.BAD_REQUEST.value(), false),
            arguments("not-an-email", "", HttpStatus.BAD_REQUEST.value(), false),
            arguments("test-4@test.org", "test-name-4", HttpStatus.CREATED.value(), true),
            arguments("test-4@test.org", "test-name-4", HttpStatus.CREATED.value(), true), // repeated input
            arguments("test-5@test.org", "test-name-5", HttpStatus.INTERNAL_SERVER_ERROR.value(), true)
        );
    }

    @ParameterizedTest
    @MethodSource("signInTestCases")
    void signIn(String email, int expectedResponseStatus, boolean isExpectingSignInTokenDispatch) throws Exception {
        // create auth user for test-cases that expect it.
        final AuthUser authUser;
        if ("existing".equals(email)) {
            authUser = createAuthUser(entityManager);
            email = authUser.getEmail();
        } else {
            authUser = null;
        }

        doNothing().when(signInTokenDispatchStrategy).dispatch(any(), any());
        mockMvc.perform(
                post("/v1/accounts/signIn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignInParams(email))))
            .andExpect(status().is(expectedResponseStatus));

        val tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(isExpectingSignInTokenDispatch ? 1 : 0))
            .dispatch(tokenCaptor.capture(), eq(email));

        if (isExpectingSignInTokenDispatch) {
            assertValidJWT(hmacSecret, tokenCaptor.getValue());
        }

        if (authUser != null) {
            entityManager.flush();
            assertNotEquals((short) 0, authUser.getIncompleteSignInAttempts());
            assertNotNull(authUser.getLastSignInAttemptAt());
        }
    }

    static Stream<Arguments> signInTestCases() {
        return Stream.of(
            // email, expectedResponseStatus, isExpectingSignInTokenDispatch
            arguments(null, HttpStatus.BAD_REQUEST.value(), false),
            arguments("", HttpStatus.BAD_REQUEST.value(), false),
            arguments("not-an-email", HttpStatus.BAD_REQUEST.value(), false),
            arguments("non-existing@test.org", HttpStatus.CREATED.value(), false),
            arguments("existing", HttpStatus.CREATED.value(), true)
        );
    }

    @ParameterizedTest
    @MethodSource("signOutTestCases")
    void signOut_withHeader(JwtType tokenType, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        var refreshJwt = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
        val accessJwt = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);
        mockMvc.perform(
                get("/v1/accounts/signOut")
                    .header("Authorization", "Bearer " + accessJwt)
                    .header(AccountController.REFRESH_TOKEN_HEADER, refreshJwt))
            .andExpect(status().is(expectedResponseStatus));

        if (expectedResponseStatus == HttpStatus.NO_CONTENT.value()) {
            // retry request with the same access token
            refreshJwt = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
            mockMvc.perform(
                    get("/v1/accounts/signOut")
                        .header("Authorization", "Bearer " + accessJwt)
                        .header(AccountController.REFRESH_TOKEN_HEADER, refreshJwt))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        }
    }

    @ParameterizedTest
    @MethodSource("signOutTestCases")
    void signOut_withCookie(JwtType tokenType, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        var refreshJwt = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
        val accessJwt = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);
        mockMvc.perform(
                get("/v1/accounts/signOut")
                    .cookie(new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, accessJwt))
                    .cookie(new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, refreshJwt)))
            .andExpect(status().is(expectedResponseStatus));

        if (expectedResponseStatus == HttpStatus.NO_CONTENT.value()) {
            // retry request with the same access token
            refreshJwt = createSignedRefreshJwt(entityManager, hmacSecret, authUser, tokenType);
            mockMvc.perform(
                    get("/v1/accounts/signOut")
                        .cookie(new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, accessJwt))
                        .cookie(new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, refreshJwt)))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        }
    }

    static Stream<Arguments> signOutTestCases() {
        return Stream.of(
            // tokenType, expectedResponseStatus
            arguments(JwtType.EMPTY, HttpStatus.BAD_REQUEST.value()),
            arguments(JwtType.VALID, HttpStatus.NO_CONTENT.value()),
            arguments(JwtType.INVALID, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.REUSED, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, HttpStatus.NO_CONTENT.value())
        );
    }

    @ParameterizedTest
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
            assertNull(authUser.getLastSignInAttemptAt());
            assertEquals((short) 0, authUser.getIncompleteSignInAttempts());
        }
    }

    static Stream<Arguments> issueCredentialsTestCases() {
        return Stream.of(
            // tokenType, userAgent, expectedResponseStatus
            arguments(JwtType.EMPTY, "test-user-agent", HttpStatus.BAD_REQUEST.value()),
            arguments(JwtType.VALID, "", HttpStatus.BAD_REQUEST.value()),
            arguments(JwtType.INVALID, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.REUSED, "test-user-agent", HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, "test-user-agent", HttpStatus.OK.value())
        );
    }

    @ParameterizedTest
    @MethodSource("emailBlacklistingTestCases")
    void emailBlacklisting(int incompleteSignInAttempts, OffsetDateTime lastSignInAttemptAt, int expectedResponseStatus) throws Exception {
        val user = createAuthUser(entityManager);
        user.setLastSignInAttemptAt(lastSignInAttemptAt);
        user.setIncompleteSignInAttempts((short) incompleteSignInAttempts);
        entityManager.persist(user);

        // perform sign-up
        mockMvc.perform(
                post("/v1/accounts/signUp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignUpParams(user.getEmail(), user.getName()))))
            .andExpect(status().is(expectedResponseStatus));

        user.setLastSignInAttemptAt(lastSignInAttemptAt);
        user.setIncompleteSignInAttempts((short) incompleteSignInAttempts);
        entityManager.persist(user);

        // perform sign-in
        mockMvc.perform(
                post("/v1/accounts/signIn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SignInParams(user.getEmail()))))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> emailBlacklistingTestCases() {
        return Stream.of(
            // incompleteSignInAttempts, lastSignInAttemptAt, responseStatus
            arguments(0, null, HttpStatus.CREATED.value()),
            arguments(5, OffsetDateTime.now().minusHours(1), HttpStatus.CREATED.value()),
            arguments(5, OffsetDateTime.now(), HttpStatus.TOO_MANY_REQUESTS.value())
        );
    }

    @Test
    void getProfile() throws Exception {
        val authUser = createAuthUser(entityManager);
        val accessToken = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);
        val result = mockMvc.perform(
                get("/v1/accounts/profile")
                    .header("Authorization", "bearer " + accessToken))
            .andExpect(status().is(HttpStatus.OK.value()))
            .andReturn();

        val profile = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(authUser.getId(), profile.findValue("accountId").asLong());
        assertEquals(authUser.getName(), profile.findValue("name").asText());
        assertEquals(authUser.getEmail(), profile.findValue("email").asText());
    }

    @ParameterizedTest
    @MethodSource("updateProfileTestCases")
    void updateProfile(String updatedName, String updatedEmail, int expectedResponseStatus) throws Exception {
        val authUser = createAuthUser(entityManager);
        val accessToken = createSignedAccessJwt(hmacSecret, authUser, JwtType.VALID);
        mockMvc.perform(
                patch("/v1/accounts/profile")
                    .header("Authorization", "bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new UpdateProfileParams(updatedEmail, updatedName))))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> updateProfileTestCases() {
        return Stream.of(
            // updatedName, updatedEmail, responseStatus
            arguments(null, null, HttpStatus.NO_CONTENT.value()),
            arguments("", null, HttpStatus.BAD_REQUEST.value()),
            arguments(null, "", HttpStatus.BAD_REQUEST.value()),
            arguments("New Name", null, HttpStatus.NO_CONTENT.value()),
            arguments(null, "new-email@api.test", HttpStatus.NO_CONTENT.value()),
            arguments("New Name", "new-email@api.test", HttpStatus.NO_CONTENT.value())
        );
    }

    @Test
    void deleteAccount() throws Exception {
        val user = createAuthUser(entityManager);
        val accessToken = createSignedAccessJwt(hmacSecret, user, JwtType.VALID);
        val anotherUser = createAuthUser(entityManager);
        val urlFmt = "/v1/accounts/{accountId}";

        val refreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, user))
            .collect(Collectors.toUnmodifiableList());

        // try deleting someone else's account
        mockMvc.perform(
                delete(urlFmt, anotherUser.getId())
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

        // delete auth user's account
        mockMvc.perform(
                delete(urlFmt, user.getId())
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(HttpStatus.NO_CONTENT.value()));

        // validate that all existing refresh tokens have been revoked.
        refreshTokens.forEach(t -> assertNull(entityManager.find(RefreshToken.class, t.getId())));

        // perform the request again to ensure access token no longer works
        mockMvc.perform(
                delete(urlFmt, anotherUser.getId())
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }
}
