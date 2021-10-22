package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.models.RefreshToken;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthConfiguration authConfiguration;

    @Mock
    private SignInTokenDispatchStrategy signInTokenDispatchStrategy;

    private AccountService service;

    @BeforeEach
    void setUp() {
        when(authConfiguration.getHmacSecret()).thenReturn(TEST_HMAC_SECRET);
        this.service = new AccountService(authUserRepository, refreshTokenRepository, authConfiguration, signInTokenDispatchStrategy);
    }

    @Test
    void signUp_withExistingAccount() throws SignInTokenDispatchException {
        val testEmail = "test-0@test.org";
        val testName = "test-name-0";
        val authUser = buildAuthUser(testEmail, testName);
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(testEmail, testName);

        verify(authUserRepository, times(0)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), eq(testEmail));

        assertValidJWT(refreshTokenCaptor.getValue());
    }

    @Test
    void signUp_withNonExistingAccount() throws SignInTokenDispatchException {
        val testEmail = "test-1@test.org";
        val testName = "test-name-1";
        val authUser = buildAuthUser(testEmail, testName);
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenReturn(authUser);
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(testEmail, testName);

        verify(authUserRepository, times(1)).save(any());
        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), eq(testEmail));

        assertValidJWT(refreshTokenCaptor.getValue());
    }

    @Test
    void signIn_withExistingAccount() throws AccountNotFoundException, SignInTokenDispatchException {
        val testEmail = "test-2@test.org";
        val authUser = buildAuthUser(testEmail, "test-name-2");
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signIn(testEmail);

        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), eq(testEmail));

        assertValidJWT(refreshTokenCaptor.getValue());
    }

    @Test
    void signIn_withNonExistingAccount() {
        val testEmail = "test-3@test.org";
        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.signIn(testEmail));
        verify(refreshTokenRepository, times(0)).save(any());
        verifyNoInteractions(signInTokenDispatchStrategy);
    }

    @Test
    void issueAuthCredentials_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials("invalid-jwt", "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withExpiredJWT() {
        val expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.HOUR, -1);
        val token = JWT.create()
            .withExpiresAt(expiresAt.getTime())
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(token, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withUsedJWT() {
        val refreshToken = buildRefreshToken(
            buildAuthUser("test-4@test.org", "test-name-4"));

        val expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.HOUR, 1);
        val token = JWT.create()
            .withJWTId("" + refreshToken.getId())
            .withClaim(AccountService.REFRESH_TOKEN_ORDINAL_CLAIM, refreshToken.getVersion() - 1)
            .withExpiresAt(expiresAt.getTime())
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(token, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withValidJWT() throws RefreshTokenVerificationException {
        val refreshToken = buildRefreshToken(
            buildAuthUser("test-5@test.org", "test-name-5"));

        val expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.HOUR, 1);
        val token = JWT.create()
            .withJWTId("" + refreshToken.getId())
            .withClaim(AccountService.REFRESH_TOKEN_ORDINAL_CLAIM, refreshToken.getVersion())
            .withExpiresAt(expiresAt.getTime())
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(refreshTokenRepository.save(refreshToken))
            .thenReturn(refreshToken);
        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        val credentials = service.issueAuthCredentials(token, "test-user-agent");
        verify(refreshTokenRepository, times(1)).save(refreshToken);
        assertValidJWT(credentials.getRefreshToken());
        assertValidJWT(credentials.getAccessToken());
    }

    @Test
    void verifyBearerJWT() {
        val invalidToken = "invalid-token";
        assertNull(service.verifyBearerJWT(invalidToken));

        val principalId = 0;
        val validToken = JWT.create()
            .withSubject("" + principalId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(authUserRepository.findActiveById(principalId))
            .thenReturn(Optional.of(mock(AuthUser.class)));

        val auth = service.verifyBearerJWT(validToken);
        assertNotNull(auth);
        assertNotNull(auth.getPrincipal());
        verify(authUserRepository, times(1)).findActiveById(principalId);
    }

    @NonNull
    private AuthUser buildAuthUser(@NonNull String email, @NonNull String name) {
        // mock since basic entity non-null fields can not be set explicitly.
        val authUser = mock(AuthUser.class);
        lenient().when(authUser.getId()).thenReturn(1);
        lenient().when(authUser.getEmail()).thenReturn(email);
        lenient().when(authUser.getName()).thenReturn(name);
        return authUser;
    }

    @NonNull
    private RefreshToken buildRefreshToken(@NonNull AuthUser authUser) {
        // mock since basic entity non-null fields can not be set explicitly.
        val refreshToken = mock(RefreshToken.class);
        lenient().when(refreshToken.getId()).thenReturn(1L);
        lenient().when(refreshToken.getVersion()).thenReturn(1L);
        lenient().when(refreshToken.getOwner()).thenReturn(authUser);
        lenient().when(refreshToken.getUserAgent()).thenReturn("test-user-agent");
        lenient().when(refreshToken.getExpiresAt()).thenReturn(LocalDateTime.now().plus(Duration.ofHours(1)));
        return refreshToken;
    }

    private void assertValidJWT(@NonNull String token) {
        JWT.require(Algorithm.HMAC256(TEST_HMAC_SECRET)).build().verify(token);
    }
}
