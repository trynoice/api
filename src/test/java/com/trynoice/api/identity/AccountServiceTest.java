package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.benmanes.caffeine.cache.Cache;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.AuthUserRepository;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.identity.entities.RefreshTokenRepository;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.DuplicateEmailException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.payload.SignInParams;
import com.trynoice.api.identity.payload.SignUpParams;
import com.trynoice.api.identity.payload.UpdateProfileParams;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private Cache<String, Boolean> revokedAccessTokenCache;

    @Mock
    private Cache<Long, Boolean> deleteUserIdCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Algorithm jwtAlgorithm;
    private AccountService service;

    @BeforeEach
    void setUp() {
        lenient().when(authConfiguration.getHmacSecret()).thenReturn(TEST_HMAC_SECRET);
        lenient().when(authConfiguration.getSignInReattemptMaxDelay()).thenReturn(Duration.ofMinutes(5));
        this.jwtAlgorithm = Algorithm.HMAC256(TEST_HMAC_SECRET);
        this.service = new AccountService(
            authUserRepository,
            refreshTokenRepository,
            authConfiguration,
            signInTokenDispatchStrategy,
            revokedAccessTokenCache,
            deleteUserIdCache,
            eventPublisher);
    }

    @Test
    void signUp_withExistingAccount() throws TooManySignInAttemptsException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
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

        when(authUserRepository.findByEmail(authUser.getEmail())).thenReturn(Optional.empty());
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
        authUser.setLastSignInAttemptAt(OffsetDateTime.now());
        authUser.setIncompleteSignInAttempts((short) 5);
        when(authUserRepository.findByEmail(authUser.getEmail())).thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenReturn(authUser);

        assertThrows(TooManySignInAttemptsException.class, () ->
            service.signUp(new SignUpParams(authUser.getEmail(), authUser.getName())));
    }

    @Test
    void signUp_emailCaseInsensitivity() {
        val email = "ABcD@api.test";
        val authUser = buildAuthUser();
        authUser.setEmail(email);
        val refreshToken = buildRefreshToken(authUser);
        when(authUserRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(authUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        assertDoesNotThrow(() -> service.signUp(new SignUpParams(authUser.getEmail(), authUser.getName())));
        verify(authUserRepository, atLeastOnce()).save(argThat(a -> a.getEmail().equals(email.toLowerCase())));
    }

    @Test
    void signIn_withExistingAccount() throws AccountNotFoundException, TooManySignInAttemptsException {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);

        when(authUserRepository.findByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
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
        when(authUserRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.signIn(new SignInParams(testEmail)));
        verify(refreshTokenRepository, times(0)).save(any());
        verifyNoInteractions(signInTokenDispatchStrategy);
    }

    @Test
    void signIn_withBlacklistedEmail() {
        val authUser = buildAuthUser();
        authUser.setLastSignInAttemptAt(OffsetDateTime.now());
        authUser.setIncompleteSignInAttempts((short) 5);
        when(authUserRepository.findByEmail(authUser.getEmail())).thenReturn(Optional.of(authUser));
        assertThrows(TooManySignInAttemptsException.class, () -> service.signIn(new SignInParams(authUser.getEmail())));
    }

    @Test
    void signIn_emailCaseInsensitivity() {
        val email = "ABcD@api.test";
        val authUser = buildAuthUser();
        authUser.setEmail(email);
        val refreshToken = buildRefreshToken(authUser);
        when(authUserRepository.findByEmail(email.toLowerCase())).thenReturn(Optional.of(authUser));
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);
        assertDoesNotThrow(() -> service.signIn(new SignInParams(authUser.getEmail())));
    }

    @Test
    void signOut_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut("invalid-jwt", "valid-acess-jwt"));
    }

    @Test
    void signOut_withExpiredJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        refreshToken.setExpiresAt(OffsetDateTime.now().minus(Duration.ofHours(1)));
        val signedJwt = refreshToken.toSignedJwt(jwtAlgorithm);
        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(signedJwt, "valid-acess-jwt"));
    }

    @Test
    void signOut_withUsedJWT() {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);
        val usedRefreshToken = buildRefreshToken(authUser);
        usedRefreshToken.setOrdinal(refreshToken.getOrdinal() - 1);
        val signedJwt = usedRefreshToken.toSignedJwt(jwtAlgorithm);

        when(refreshTokenRepository.findById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () -> service.signOut(signedJwt, "valid-acess-jwt"));
    }

    @Test
    void signOut_withValidJWT() {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val signedJwt = refreshToken.toSignedJwt(jwtAlgorithm);
        val accessToken = "valid-acess-jwt";

        when(refreshTokenRepository.findById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.signOut(signedJwt, accessToken);
        });

        assertTrue(refreshToken.getExpiresAt().isBefore(OffsetDateTime.now()));
        verify(revokedAccessTokenCache, times(1)).put(accessToken, Boolean.TRUE);
    }

    @Test
    void issueAuthCredentials_withInvalidJWT() {
        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials("invalid-jwt", "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withExpiredJWT() {
        val expiredRefreshToken = buildRefreshToken(buildAuthUser());
        expiredRefreshToken.setExpiresAt(OffsetDateTime.now().minus(Duration.ofHours(1)));
        val signedJwt = expiredRefreshToken.toSignedJwt(jwtAlgorithm);

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(signedJwt, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withUsedJWT() {
        val authUser = buildAuthUser();
        val refreshToken = buildRefreshToken(authUser);
        val usedRefreshToken = buildRefreshToken(authUser);
        usedRefreshToken.setOrdinal(refreshToken.getOrdinal() - 1);
        val signedJwt = usedRefreshToken.toSignedJwt(jwtAlgorithm);

        when(refreshTokenRepository.findById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        assertThrows(RefreshTokenVerificationException.class, () ->
            service.issueAuthCredentials(signedJwt, "test-user-agent"));
    }

    @Test
    void issueAuthCredentials_withMalformedJWT() {
        val refreshToken = JWT.create().sign(jwtAlgorithm);
        assertThrows(RefreshTokenVerificationException.class, () -> service.issueAuthCredentials(refreshToken, ""));
    }

    @Test
    void issueAuthCredentials_withValidJWT() throws RefreshTokenVerificationException {
        val refreshToken = buildRefreshToken(buildAuthUser());
        val token = refreshToken.toSignedJwt(jwtAlgorithm);

        when(refreshTokenRepository.save(refreshToken))
            .thenReturn(refreshToken);
        when(refreshTokenRepository.findById(refreshToken.getId()))
            .thenReturn(Optional.of(refreshToken));

        val credentials = service.issueAuthCredentials(token, "test-user-agent");
        verify(refreshTokenRepository, times(1)).save(refreshToken);
        assertValidJwt(credentials.getRefreshToken());
        assertValidJwt(credentials.getAccessToken());

        // must reset signInAttempts.
        assertEquals((short) 0, refreshToken.getOwner().getIncompleteSignInAttempts());
        verify(authUserRepository, times(1)).save(refreshToken.getOwner());
    }

    @Test
    void getProfile() {
        val authUser = buildAuthUser();
        when(authUserRepository.findById(authUser.getId()))
            .thenReturn(Optional.of(authUser));

        val profile = service.getProfile(authUser.getId());
        assertEquals(authUser.getId(), profile.getAccountId());
        assertEquals(authUser.getName(), profile.getName());
        assertEquals(authUser.getEmail(), profile.getEmail());
    }

    @Test
    void updateProfile_withConflictingEmails() {
        val authUser = buildAuthUser();
        val updatedEmail = "test-name-2@api.test";
        when(authUserRepository.findById(authUser.getId()))
            .thenReturn(Optional.of(authUser));

        when(authUserRepository.existsByEmail(updatedEmail)).thenReturn(true);
        assertThrows(
            DuplicateEmailException.class,
            () -> service.updateProfile(authUser.getId(), new UpdateProfileParams(updatedEmail, null)));

        verify(authUserRepository, times(0)).save(any());
    }

    @Test
    void updateProfile_withNoChange() {
        val authUser = buildAuthUser();
        when(authUserRepository.findById(authUser.getId()))
            .thenReturn(Optional.of(authUser));

        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.updateProfile(authUser.getId(), new UpdateProfileParams(authUser.getEmail(), authUser.getName()));
        });

        verify(authUserRepository, times(1)).save(authUser);
    }

    @Test
    void updateProfile_withUpdatedFields() {
        val authUser = buildAuthUser();
        val updatedEmail = "new-name@api.test";
        val updatedName = "New Name";
        when(authUserRepository.findById(authUser.getId()))
            .thenReturn(Optional.of(authUser));

        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.updateProfile(authUser.getId(), new UpdateProfileParams(updatedEmail, updatedName));
        });

        verify(authUserRepository, times(1)).save(authUser);
        assertEquals(updatedEmail, authUser.getEmail());
        assertEquals(updatedName, authUser.getName());
    }

    @Test
    void verifyAccessToken_withInvalidToken() {
        val invalidToken = "invalid-token";
        assertNull(service.verifyAccessToken(invalidToken));
    }

    @Test
    void verifyAccessToken_withValidToken() {
        val principalId = 0L;
        val validToken = JWT.create()
            .withSubject("" + principalId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        val auth = service.verifyAccessToken(validToken);
        assertNotNull(auth);
        assertNotNull(auth.getPrincipal());
    }

    @Test
    void verifyAccessToken_withRevokedToken() {
        val principalId = 0L;
        val validToken = JWT.create()
            .withSubject("" + principalId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(revokedAccessTokenCache.getIfPresent(validToken))
            .thenReturn(Boolean.TRUE);

        assertNull(service.verifyAccessToken(validToken));
    }

    private void assertValidJwt(@NonNull String token) {
        JWT.require(jwtAlgorithm).build().verify(token);
    }

    @Test
    void performGarbageCollection() {
        val deactivatedUser = buildAuthUser();
        when(authUserRepository.findAllIdsDeactivatedBefore(any()))
            .thenReturn(List.of(deactivatedUser.getId()));

        service.performGarbageCollection();
        verify(refreshTokenRepository, times(1)).deleteAllExpiredBefore(any());
        verify(refreshTokenRepository, times(1)).deleteAllByOwnerId(deactivatedUser.getId());
        verify(authUserRepository, times(1)).deleteById(deactivatedUser.getId());
        verify(eventPublisher, times(1)).publishEvent(argThat((Object e) ->
            ((AccountServiceContract.UserDeletedEvent) e).getUserId() == deactivatedUser.getId()));
    }

    @NonNull
    private static AuthUser buildAuthUser() {
        return AuthUser.builder()
            .id((long) (Math.random() * 1000) + 1)
            .email("test-name@api.test")
            .name("test-name")
            .build();
    }

    @NonNull
    private static RefreshToken buildRefreshToken(@NonNull AuthUser authUser) {
        return RefreshToken.builder()
            .id(1)
            .owner(authUser)
            .userAgent("test-user-agent")
            .expiresAt(OffsetDateTime.now().plus(Duration.ofHours(1)))
            .build();
    }
}
