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

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail()))
            .thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(authUser.getEmail(), authUser.getName());

        verify(authUserRepository, times(0)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJWT(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signUp_withNonExistingAccount() throws SignInTokenDispatchException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail()))
            .thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenReturn(authUser);
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(authUser.getEmail(), authUser.getName());

        verify(authUserRepository, times(1)).save(any());
        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJWT(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signIn_withExistingAccount() throws AccountNotFoundException, SignInTokenDispatchException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail()))
            .thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signIn(authUser.getEmail());

        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJWT(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signIn_withNonExistingAccount() {
        val testEmail = "test@test.org";
        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.signIn(testEmail));
        verify(refreshTokenRepository, times(0)).save(any());
        verifyNoInteractions(signInTokenDispatchStrategy);
    }

    @Test
    void signOut_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut("invalid-jwt"));
    }

    @Test
    void signOut_withExpiredJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().minus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, null);
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(token));
    }

    @Test
    void signOut_withUsedJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, refreshToken.getVersion() - 1);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(token));
    }

    @Test
    void signOut_withValidJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, null);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        // error without block: java: incompatible types: inference variable T has incompatible bounds
        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.signOut(token);
        });

        verify(refreshTokenRepository, times(1)).delete(refreshToken);
    }

    @Test
    void issueAuthCredentials_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials("invalid-jwt", "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withExpiredJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().minus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, null);

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(token, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withUsedJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, refreshToken.getVersion() - 1);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(token, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withValidJWT() throws RefreshTokenVerificationException {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        val token = signRefreshToken(refreshToken, expiresAt, null);

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
    private AuthUser buildAuthUser() {
        // mock since basic entity non-null fields can not be set explicitly.
        val authUser = mock(AuthUser.class);
        lenient().when(authUser.getId()).thenReturn(1);
        lenient().when(authUser.getEmail()).thenReturn("test@test.org");
        lenient().when(authUser.getName()).thenReturn("test-name");
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

    @NonNull
    private String signRefreshToken(
        @NonNull RefreshToken refreshToken,
        @NonNull LocalDateTime expiresAt,
        Long jwtVersion
    ) {
        return JWT.create()
            .withJWTId("" + refreshToken.getId())
            .withClaim(
                AccountService.REFRESH_TOKEN_ORDINAL_CLAIM,
                Objects.requireNonNullElse(jwtVersion, refreshToken.getVersion()))
            .withExpiresAt(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));
    }

    private void assertValidJWT(@NonNull String token) {
        JWT.require(Algorithm.HMAC256(TEST_HMAC_SECRET)).build().verify(token);
    }
}
