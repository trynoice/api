package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenRevokeException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.SignInParams;
import com.trynoice.api.identity.models.SignUpParams;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private Algorithm jwtAlgorithm;
    private AccountService service;

    @BeforeEach
    void setUp() {
        when(authConfiguration.getHmacSecret()).thenReturn(TEST_HMAC_SECRET);
        this.jwtAlgorithm = Algorithm.HMAC256(TEST_HMAC_SECRET);
        this.service = new AccountService(authUserRepository, refreshTokenRepository, authConfiguration, signInTokenDispatchStrategy);
    }

    @Test
    void signUp_withExistingAccount() throws TooManySignInAttemptsException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(new SignUpParams(authUser.getEmail(), authUser.getName()));

        verify(authUserRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJwt(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signUp_withNonExistingAccount() throws TooManySignInAttemptsException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail())).thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenReturn(authUser);
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signUp(new SignUpParams(authUser.getEmail(), authUser.getName()));

        verify(authUserRepository, times(2)).save(any());
        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJwt(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signUp_withBlacklistedEmail() {
        val authUser = buildAuthUser();
        authUser.setSignInAttempts(AccountService.MAX_SIGN_IN_ATTEMPTS_PER_USER);
        when(authUserRepository.findActiveByEmail(authUser.getEmail())).thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenReturn(authUser);

        assertThrows(TooManySignInAttemptsException.class, () ->
            service.signUp(new SignUpParams(authUser.getEmail(), authUser.getName())));
    }

    @Test
    void signIn_withExistingAccount() throws AccountNotFoundException, TooManySignInAttemptsException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findActiveByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        service.signIn(new SignInParams(authUser.getEmail()));

        verify(refreshTokenRepository, times(1)).save(any());
        val refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        val destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(signInTokenDispatchStrategy, times(1))
            .dispatch(refreshTokenCaptor.capture(), destinationCaptor.capture());

        assertValidJwt(refreshTokenCaptor.getValue());
        assertEquals(authUser.getEmail(), destinationCaptor.getValue());
    }

    @Test
    void signIn_withNonExistingAccount() {
        val testEmail = "test@test.org";
        when(authUserRepository.findActiveByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.signIn(new SignInParams(testEmail)));
        verify(refreshTokenRepository, times(0)).save(any());
        verifyNoInteractions(signInTokenDispatchStrategy);
    }

    @Test
    void signIn_withBlacklistedEmail() {
        val authUser = buildAuthUser();
        authUser.setSignInAttempts(AccountService.MAX_SIGN_IN_ATTEMPTS_PER_USER);
        when(authUserRepository.findActiveByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
        assertThrows(TooManySignInAttemptsException.class, () -> service.signIn(new SignInParams(authUser.getEmail())));
    }

    @Test
    void signOut_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut("invalid-jwt"));
    }

    @Test
    void signOut_withExpiredJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        refreshToken.setExpiresAt(LocalDateTime.now().minus(Duration.ofHours(1)));
        val signedJwt = refreshToken.getJwt(jwtAlgorithm);
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(signedJwt));
    }

    @Test
    void signOut_withUsedJWT() {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);
        val usedRefreshToken = buildRefreshToken(authUser);
        usedRefreshToken.setOrdinal(refreshToken.getOrdinal() - 1);
        val signedJwt = usedRefreshToken.getJwt(jwtAlgorithm);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(signedJwt));
    }

    @Test
    void signOut_withValidJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val signedJwt = refreshToken.getJwt(jwtAlgorithm);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        // error without block: java: incompatible types: inference variable T has incompatible bounds
        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.signOut(signedJwt);
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
        val expiredRefreshToken = buildRefreshToken(buildAuthUser());
        expiredRefreshToken.setExpiresAt(LocalDateTime.now().minus(Duration.ofHours(1)));
        val signedJwt = expiredRefreshToken.getJwt(jwtAlgorithm);

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(signedJwt, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withUsedJWT() {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);
        val usedRefreshToken = buildRefreshToken(authUser);
        usedRefreshToken.setOrdinal(refreshToken.getOrdinal() - 1);
        val signedJwt = usedRefreshToken.getJwt(jwtAlgorithm);

        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(signedJwt, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withValidJWT() throws RefreshTokenVerificationException {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val token = refreshToken.getJwt(jwtAlgorithm);

        when(refreshTokenRepository.save(refreshToken))
            .thenReturn(refreshToken);
        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        val credentials = service.issueAuthCredentials(token, "test-user-agent");
        verify(refreshTokenRepository, times(1)).save(refreshToken);
        assertValidJwt(credentials.getRefreshToken());
        assertValidJwt(credentials.getAccessToken());

        // must reset signInAttempts.
        assertEquals((short) 0, refreshToken.getOwner().getSignInAttempts());
        verify(authUserRepository, times(1)).save(refreshToken.getOwner());
    }

    @Test
    void revokeRefreshToken() {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);
        when(refreshTokenRepository.findActiveById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.revokeRefreshToken(authUser, refreshToken.getId());
        });

        val anotherAuthUser = buildAuthUser();
        anotherAuthUser.setId(2L);
        assertThrows(
            RefreshTokenRevokeException.class,
            () -> service.revokeRefreshToken(anotherAuthUser, refreshToken.getId()));
    }

    @Test
    void getProfile() {
        val authUser = buildAuthUser();
        val refreshTokens = List.of(buildRefreshToken(authUser));
        when(refreshTokenRepository.findAllActiveAndUnexpiredByOwner(authUser)).thenReturn(refreshTokens);

        val profile = service.getProfile(authUser);
        assertEquals(authUser.getId(), profile.getAccountId());
        assertEquals(authUser.getName(), profile.getName());
        assertEquals(authUser.getEmail(), profile.getEmail());

        val activeSessions = profile.getActiveSessions();
        for (int i = 0; i < activeSessions.size(); i++) {
            assertEquals(refreshTokens.get(i).getId(), activeSessions.get(i).getRefreshTokenId());
            assertEquals(refreshTokens.get(i).getCreatedAt(), activeSessions.get(i).getCreatedAt());
            assertEquals(refreshTokens.get(i).getLastUsedAt(), activeSessions.get(i).getLastUsedAt());
            assertEquals(refreshTokens.get(i).getUserAgent(), activeSessions.get(i).getUserAgent());
        }
    }

    @Test
    void verifyAccessToken() {
        val invalidToken = "invalid-token";
        assertNull(service.verifyAccessToken(invalidToken));

        val principalId = 0L;
        val validToken = JWT.create()
            .withSubject("" + principalId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(authUserRepository.findActiveById(principalId))
            .thenReturn(Optional.of(mock(AuthUser.class)));

        val auth = service.verifyAccessToken(validToken);
        assertNotNull(auth);
        assertNotNull(auth.getPrincipal());
        verify(authUserRepository, times(1)).findActiveById(principalId);
    }

    @NonNull
    private AuthUser buildAuthUser() {
        val authUser = AuthUser.builder()
            .email("test-name@api.test")
            .name("test-name")
            .build();

        authUser.setId(1L);
        authUser.setCreatedAt(LocalDateTime.now());
        return authUser;
    }

    @NonNull
    private RefreshToken buildRefreshToken(@NonNull AuthUser authUser) {
        val refreshToken = RefreshToken.builder()
            .owner(authUser)
            .userAgent("test-user-agent")
            .expiresAt(LocalDateTime.now().plus(Duration.ofHours(1)))
            .build();

        refreshToken.setId(1L);
        refreshToken.setCreatedAt(LocalDateTime.now());
        return refreshToken;
    }

    private void assertValidJwt(@NonNull String token) {
        JWT.require(jwtAlgorithm).build().verify(token);
    }
}
