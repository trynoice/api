package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.github.benmanes.caffeine.cache.Cache;
import com.trynoice.api.config.GlobalConfiguration;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.AuthUserRepository;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.identity.entities.RefreshTokenRepository;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.BearerJwtAuthenticationException;
import com.trynoice.api.identity.exceptions.DuplicateEmailException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.payload.AuthCredentialsResponse;
import com.trynoice.api.identity.payload.ProfileResponse;
import com.trynoice.api.identity.payload.SignInParams;
import com.trynoice.api.identity.payload.SignUpParams;
import com.trynoice.api.identity.payload.UpdateProfileParams;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.Long.min;
import static java.lang.Long.parseLong;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNullElse;

/**
 * {@link AccountService} implements operations related to account management and auth.
 */
@Service
@Slf4j
class AccountService implements AccountServiceContract {

    private static final long MIN_SIGN_IN_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GlobalConfiguration globalConfig;
    private final AuthConfiguration authConfig;
    private final SignInTokenDispatchStrategy signInTokenDispatchStrategy;
    private final Algorithm jwtAlgorithm;
    private final JWTVerifier jwtVerifier;
    private final Cache<String, Boolean> revokedAccessJwtCache;
    private final Cache<Long, Boolean> deletedUserIdCache;

    @Autowired
    AccountService(
        @NonNull AuthUserRepository authUserRepository,
        @NonNull RefreshTokenRepository refreshTokenRepository,
        @NonNull GlobalConfiguration globalConfig,
        @NonNull AuthConfiguration authConfig,
        @NonNull SignInTokenDispatchStrategy signInTokenDispatchStrategy,
        @NonNull @Qualifier(AuthBeans.REVOKED_ACCESS_JWT_CACHE) Cache<String, Boolean> revokedAccessJwtCache,
        @NonNull @Qualifier(AuthBeans.DELETED_USER_ID_CACHE) Cache<Long, Boolean> deletedUserIdCache
    ) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.globalConfig = globalConfig;
        this.authConfig = authConfig;
        this.signInTokenDispatchStrategy = signInTokenDispatchStrategy;
        this.revokedAccessJwtCache = revokedAccessJwtCache;
        this.deletedUserIdCache = deletedUserIdCache;
        this.jwtAlgorithm = Algorithm.HMAC256(authConfig.getHmacSecret());
        this.jwtVerifier = JWT.require(this.jwtAlgorithm).build();
    }

    /**
     * Creates a new user account with the provided email and name if one didn't already exist and
     * then sends a sign-in link to the accounts email.
     *
     * @param params sign-up parameters
     * @throws SignInTokenDispatchException   if email cannot be sent (due to upstream service error).
     * @throws TooManySignInAttemptsException if auth user makes too many attempts without a successful sign-in.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void signUp(@NonNull SignUpParams params) throws TooManySignInAttemptsException {
        params.setEmail(params.getEmail().toLowerCase());
        val user = authUserRepository.findByEmail(params.getEmail())
            .orElseGet(() -> authUserRepository.save(
                AuthUser.builder()
                    .email(params.getEmail())
                    .name(params.getName())
                    .build()));

        val refreshToken = createSignInToken(user);
        signInTokenDispatchStrategy.dispatch(refreshToken, params.getEmail());
    }

    /**
     * Checks if an account with the given email exists and then sends a sign-in link to the email.
     *
     * @param params sign-in parameters
     * @throws AccountNotFoundException       if an account with the given email doesn't exist.
     * @throws SignInTokenDispatchException   if email cannot be sent (due to upstream service error).
     * @throws TooManySignInAttemptsException if auth user makes too many attempts without a successful sign-in.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void signIn(@NonNull SignInParams params) throws AccountNotFoundException, TooManySignInAttemptsException {
        params.setEmail(params.getEmail().toLowerCase());
        val user = authUserRepository.findByEmail(params.getEmail())
            .orElseThrow(() -> {
                val msg = String.format("account with email '%s' doesn't exist", params.getEmail());
                return new AccountNotFoundException(msg);
            });

        val refreshToken = createSignInToken(user);
        signInTokenDispatchStrategy.dispatch(refreshToken, params.getEmail());
    }

    @NonNull
    private String createSignInToken(@NonNull AuthUser authUser) throws TooManySignInAttemptsException {
        if (authUser.getLastSignInAttemptAt() != null) {
            val factor = round(pow(2, authUser.getIncompleteSignInAttempts()));
            val delay = min(factor * MIN_SIGN_IN_DELAY_MILLIS, authConfig.getSignInReattemptMaxDelay().toMillis());
            val nextAttemptAt = authUser.getLastSignInAttemptAt().plus(delay, ChronoUnit.MILLIS);
            val now = OffsetDateTime.now();
            if (nextAttemptAt.isAfter(now)) {
                throw new TooManySignInAttemptsException(authUser.getEmail(), Duration.between(now, nextAttemptAt));
            }
        }

        authUser.updateSignInAttemptData();
        authUserRepository.save(authUser);
        return refreshTokenRepository.save(
                RefreshToken.builder()
                    .owner(authUser)
                    .expiresAt(OffsetDateTime.now().plus(authConfig.getSignInTokenExpiry()))
                    .build())
            .getJwt(jwtAlgorithm);
    }

    /**
     * Revokes a pair valid refresh and access tokens. The service assumes that the given {@code
     * accessJwt} has been validated by the caller before making the call.
     *
     * @param refreshJwt refresh token provided by the client.
     * @param accessJwt  access token provided by the client.
     * @throws RefreshTokenVerificationException if the refresh token is invalid, expired or re-used.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void signOut(@NonNull String refreshJwt, @NonNull String accessJwt) throws RefreshTokenVerificationException {
        val refreshToken = verifyRefreshJWT(refreshJwt);
        refreshTokenRepository.delete(refreshToken);
        revokedAccessJwtCache.put(accessJwt, Boolean.TRUE);
    }

    /**
     * Verifies if the given refresh token is valid and then returns a fresh set of auth
     * credentials.
     *
     * @param refreshToken refresh token provided by the client
     * @param userAgent    user-agent that the client used to make this request. It can be {@code null}.
     * @return a fresh pair of refresh and access tokens as {@link AuthCredentialsResponse}.
     * @throws RefreshTokenVerificationException if the refresh token is invalid, expired or re-used.
     */
    @NonNull
    @Transactional(rollbackFor = Throwable.class, noRollbackFor = RefreshTokenVerificationException.class)
    public AuthCredentialsResponse issueAuthCredentials(@NonNull String refreshToken, String userAgent) throws RefreshTokenVerificationException {
        var token = verifyRefreshJWT(refreshToken);

        // ordinal 0 implies that this refresh token is being used to sign in, so persist userAgent
        // and reset sign-in attempts.
        if (Long.valueOf(0).equals(token.getOrdinal())) {
            token.setUserAgent(requireNonNullElse(userAgent, ""));
            token.getOwner().resetSignInAttemptData();
        }

        // saving AuthUser entity implicitly updates last active timestamp, so always perform the
        // save step regardless of the token ordinal value.
        authUserRepository.save(token.getOwner());
        token.setExpiresAt(OffsetDateTime.now().plus(authConfig.getRefreshTokenExpiry()));
        token.incrementOrdinal();
        token = refreshTokenRepository.save(token);

        val accessTokenExpiry = OffsetDateTime.now().plus(authConfig.getAccessTokenExpiry());
        val signedAccessToken = JWT.create()
            .withSubject("" + token.getOwner().getId())
            .withExpiresAt(Date.from(accessTokenExpiry.toInstant()))
            .sign(jwtAlgorithm);

        return AuthCredentialsResponse.builder()
            .refreshToken(token.getJwt(jwtAlgorithm))
            .accessToken(signedAccessToken)
            .build();
    }

    @NonNull
    private RefreshToken verifyRefreshJWT(@NonNull String jwt) throws RefreshTokenVerificationException {
        final long jwtId, jwtOrdinal;
        try {
            val decodedToken = jwtVerifier.verify(jwt);
            jwtId = parseLong(decodedToken.getId());
            jwtOrdinal = requireNonNullElse(decodedToken.getClaim(RefreshToken.ORD_JWT_CLAIM).asLong(), -1L);
        } catch (JWTVerificationException | NumberFormatException e) {
            throw new RefreshTokenVerificationException("refresh token verification failed", e);
        }

        val token = refreshTokenRepository.findById(jwtId)
            .orElseThrow(() -> new RefreshTokenVerificationException("refresh token doesn't exist in database"));

        // if token ordinal is different, it implies that an old refresh token is being re-used.
        // delete token on re-use to effectively sign out both the legitimate user and the attacker.
        if (token.getOrdinal() != jwtOrdinal) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenVerificationException("refresh token ordinal mismatch");
        }

        return token;
    }

    /**
     * Returns an externalised view of an account's data, containing fields that are accessible by
     * account owners.
     *
     * @return a non-null {@link ProfileResponse}.
     */
    @NonNull
    ProfileResponse getProfile(@NonNull Long userId) {
        val authUser = authUserRepository.findById(userId).orElseThrow();
        return ProfileResponse.builder()
            .accountId(authUser.getId())
            .name(authUser.getName())
            .email(authUser.getEmail())
            .build();
    }

    /**
     * Updates the profile of the user with given {@literal userId}. The {@literal null} fields in
     * {@literal params} are ignored during the update.
     *
     * @param userId a not null id of a user.
     * @param params a not null instance containing updated profile data.
     * @throws DuplicateEmailException if the updated email belongs to another existing account.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void updateProfile(@NonNull Long userId, @NonNull UpdateProfileParams params) throws DuplicateEmailException {
        val authUser = authUserRepository.findById(userId).orElseThrow();
        if (params.getEmail() != null) {
            if (!authUser.getEmail().equals(params.getEmail()) && authUserRepository.existsByEmail(params.getEmail())) {
                throw new DuplicateEmailException();
            }

            authUser.setEmail(params.getEmail());
        }

        if (params.getName() != null) {
            authUser.setName(params.getName());
        }

        authUserRepository.save(authUser);
    }

    /**
     * Deletes the account belong to the user with the given {@literal userId}.
     *
     * @param userId must not be {@literal null}.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deleteAccount(@NonNull Long userId) {
        refreshTokenRepository.deleteAllByOwnerId(userId);
        authUserRepository.deleteById(userId);
        deletedUserIdCache.put(userId, Boolean.TRUE);
    }

    @Override
    @NonNull
    public Optional<String> findEmailByUser(@NonNull Long userId) {
        return authUserRepository.findEmailById(userId);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void performGarbageCollection() {
        refreshTokenRepository.deleteAllExpired(OffsetDateTime.now());
        val deletedBefore = OffsetDateTime.now().minus(globalConfig.getRemoveDeletedEntitiesAfter());
        authUserRepository.removeAllDeleted(deletedBefore);
        refreshTokenRepository.removeAllDeleted(deletedBefore);
    }

    /**
     * Verifies if the provided JWT is valid. If the provided JWT is valid, clients can get the
     * {@code id} of the {@link AuthUser} using {@link Authentication#getPrincipal()}.
     *
     * @param token jwt to verify
     * @return a non-null {@link Authentication} if the provided token is valid. {@code null} if the
     * token is invalid.
     */
    Authentication verifyAccessToken(@NonNull String token) {
        // check if the token was revoked during sign-out.
        if (requireNonNullElse(revokedAccessJwtCache.getIfPresent(token), false)) {
            log.trace("attempted authentication with a revoked access token");
            return null;
        }

        try {
            val auth = new BearerJWT(token);
            if (!requireNonNullElse(deletedUserIdCache.getIfPresent(auth.principalId), false)) {
                return auth;
            }

            log.trace("attempted authentication for a delete user account");
        } catch (BearerJwtAuthenticationException e) {
            log.trace("access token verification failed", e);
        }

        return null;
    }

    private class BearerJWT extends AbstractAuthenticationToken {

        private final String token;
        private final Long principalId;

        private BearerJWT(@NonNull String token) {
            super(List.of());
            this.token = token;

            try {
                val decodedToken = jwtVerifier.verify(token);
                this.principalId = parseLong(decodedToken.getSubject());
            } catch (JWTVerificationException e) {
                throw new BearerJwtAuthenticationException("failed to verify jwt", e);
            } catch (NumberFormatException e) {
                throw new BearerJwtAuthenticationException("failed to parse jwt subject", e);
            }
        }

        @Override
        public Object getCredentials() {
            return token;
        }

        @Override
        public Object getPrincipal() {
            return principalId;
        }

        @Override
        public boolean isAuthenticated() {
            return principalId != null;
        }
    }
}
