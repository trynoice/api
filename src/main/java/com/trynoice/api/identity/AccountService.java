package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.github.benmanes.caffeine.cache.Cache;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.BearerJwtAuthenticationException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.Profile;
import com.trynoice.api.identity.models.SignInParams;
import com.trynoice.api.identity.models.SignUpParams;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Long.min;
import static java.lang.Long.parseLong;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * {@link AccountService} implements operations related to account management and auth.
 */
@Service
@Slf4j
class AccountService {

    private static final long MIN_SIGN_IN_REATTEMPT_DELAY_SECONDS = TimeUnit.SECONDS.toSeconds(3);

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthConfiguration authConfig;
    private final SignInTokenDispatchStrategy signInTokenDispatchStrategy;
    private final Algorithm jwtAlgorithm;
    private final JWTVerifier jwtVerifier;
    private final Cache<String, Boolean> revokedAccessJwtCache;

    @Autowired
    AccountService(
        @NonNull AuthUserRepository authUserRepository,
        @NonNull RefreshTokenRepository refreshTokenRepository,
        @NonNull AuthConfiguration authConfig,
        @NonNull SignInTokenDispatchStrategy signInTokenDispatchStrategy,
        @NonNull @Qualifier(AuthConfiguration.REVOKED_ACCESS_JWT_CACHE) Cache<String, Boolean> revokedAccessJwtCache
    ) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authConfig = authConfig;
        this.signInTokenDispatchStrategy = signInTokenDispatchStrategy;
        this.revokedAccessJwtCache = revokedAccessJwtCache;
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
        val user = authUserRepository.findActiveByEmail(params.getEmail())
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
        val user = authUserRepository.findActiveByEmail(params.getEmail())
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
            var delay = round(pow(MIN_SIGN_IN_REATTEMPT_DELAY_SECONDS, authUser.getIncompleteSignInAttempts()));
            delay = min(delay, authConfig.getSignInReattemptMaxDelay().toSeconds());
            val nextAttemptAt = authUser.getLastSignInAttemptAt().plusSeconds(delay);
            val now = LocalDateTime.now();
            if (nextAttemptAt.isAfter(now)) {
                throw new TooManySignInAttemptsException(authUser.getEmail(), Duration.between(now, nextAttemptAt));
            }
        }

        authUser.updateSignInAttemptData();
        authUserRepository.save(authUser);
        return refreshTokenRepository.save(
                RefreshToken.builder()
                    .owner(authUser)
                    .expiresAt(LocalDateTime.now().plus(authConfig.getSignInTokenExpiry()))
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
     * @return fresh {@link AuthCredentials}
     * @throws RefreshTokenVerificationException if the refresh token is invalid, expired or re-used.
     */
    @NonNull
    @Transactional(rollbackFor = Throwable.class, noRollbackFor = RefreshTokenVerificationException.class)
    public AuthCredentials issueAuthCredentials(@NonNull String refreshToken, String userAgent) throws RefreshTokenVerificationException {
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
        token.setExpiresAt(LocalDateTime.now().plus(authConfig.getRefreshTokenExpiry()));
        token.incrementOrdinal();
        token = refreshTokenRepository.save(token);

        val accessTokenExpiry = LocalDateTime.now().plus(authConfig.getAccessTokenExpiry());
        val signedAccessToken = JWT.create()
            .withSubject("" + token.getOwner().getId())
            .withExpiresAt(Date.from(accessTokenExpiry.atZone(ZoneId.systemDefault()).toInstant()))
            .sign(jwtAlgorithm);

        return AuthCredentials.builder()
            .refreshToken(token.getJwt(jwtAlgorithm))
            .accessToken(signedAccessToken)
            .build();
    }

    @NonNull
    private RefreshToken verifyRefreshJWT(@NonNull String jwt) throws RefreshTokenVerificationException {
        //noinspection WrapperTypeMayBePrimitive because jwtOrdinal could be null.
        final Long jwtId, jwtOrdinal;
        try {
            val decodedToken = jwtVerifier.verify(jwt);
            jwtId = parseLong(decodedToken.getId());
            jwtOrdinal = decodedToken.getClaim(RefreshToken.ORD_JWT_CLAIM).asLong();
        } catch (JWTVerificationException | NumberFormatException e) {
            throw new RefreshTokenVerificationException("refresh token verification failed", e);
        }

        val token = refreshTokenRepository.findActiveById(jwtId)
            .orElseThrow(() -> new RefreshTokenVerificationException("refresh token doesn't exist in database"));

        // if token ordinal is different, it implies that an old refresh token is being re-used.
        // delete token on re-use to effectively sign out both the legitimate user and the attacker.
        if (!token.getOrdinal().equals(jwtOrdinal)) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenVerificationException("refresh token ordinal mismatch");
        }

        return token;
    }

    /**
     * Returns an externalised view of an account's data, containing fields that are accessible by
     * account owners.
     *
     * @return a non-null {@link Profile}.
     */
    @NonNull
    Profile getProfile(@NonNull AuthUser authUser) {
        return Profile.builder()
            .accountId(authUser.getId())
            .name(authUser.getName())
            .email(authUser.getEmail())
            .build();
    }

    /**
     * Verifies if the provided JWT is valid. If the provided JWT is valid, clients can lazy-fetch
     * {@link AuthUser} using {@link Authentication#getPrincipal()}.
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
            return new BearerJWT(token);
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
            return authUserRepository.findActiveById(requireNonNull(principalId))
                .orElseThrow(() -> new BearerJwtAuthenticationException("account doesn't exist; it may have been closed!"));
        }

        @Override
        public boolean isAuthenticated() {
            return principalId != null;
        }
    }
}
