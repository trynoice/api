package com.trynoice.api.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.SignInRequest;
import com.trynoice.api.identity.models.SignUpRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.JwtType;
import static com.trynoice.api.testing.AuthTestUtils.assertValidJWT;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createRefreshToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @ParameterizedTest(name = "{displayName} - tokenType={0} responseStatus={1}")
    @MethodSource("signOutTestCases")
    void signOut(JwtType tokenType, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        val token = createRefreshToken(entityManager, hmacSecret, authUser, tokenType);
        mockMvc.perform(
                get("/v1/accounts/signOut")
                    .header("X-Refresh-Token", token))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> signOutTestCases() {
        return Stream.of(
            // tokenType, responseStatus
            arguments(JwtType.EMPTY, HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(JwtType.INVALID, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.EXPIRED, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.REUSED, HttpStatus.UNAUTHORIZED.value()),
            arguments(JwtType.VALID, HttpStatus.OK.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - tokenType={0} userAgent={1} responseStatus={2}")
    @MethodSource("issueCredentialsTestCases")
    void issueCredentials(JwtType tokenType, String userAgent, int expectedResponseStatus) throws Exception {
        // create signed refresh-tokens as expected by various test cases.
        val authUser = createAuthUser(entityManager);
        val token = createRefreshToken(entityManager, hmacSecret, authUser, tokenType);
        val result = mockMvc.perform(
                get("/v1/accounts/credentials")
                    .header("X-Refresh-Token", token)
                    .header("User-Agent", userAgent))
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val authCredentials = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(), AuthCredentials.class);

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
            arguments(JwtType.VALID, "", HttpStatus.OK.value()),
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
}
