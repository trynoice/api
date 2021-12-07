package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenRevokeException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.models.RefreshToken;
import com.trynoice.api.identity.viewmodels.AuthCredentialsResponse;
import com.trynoice.api.identity.viewmodels.ProfileResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNull;

/**
 * {@link AccountService} implements operations related to account management and auth.
 */
@Service
@Slf4j
public class AccountService {

    static final short MAX_SIGN_IN_ATTEMPTS_PER_USER = 5;

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthConfiguration authConfig;
    private final SignInTokenDispatchStrategy signInTokenDispatchStrategy;
    private final Algorithm jwtAlgorithm;
    private final JWTVerifier jwtVerifier;

    @Autowired
    AccountService(
        @NonNull AuthUserRepository authUserRepository,
        @NonNull RefreshTokenRepository refreshTokenRepository,
        @NonNull AuthConfiguration authConfig,
        @NonNull SignInTokenDispatchStrategy signInTokenDispatchStrategy
    ) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authConfig = authConfig;
        this.signInTokenDispatchStrategy = signInTokenDispatchStrategy;
        this.jwtAlgorithm = Algorithm.HMAC256(authConfig.getHmacSecret());
        this.jwtVerifier = JWT.require(this.jwtAlgorithm).build();
    }

    /**
     * Creates a new user account with the provided email and name if one didn't already exist and
     * then sends a sign-in link to the accounts email.
     *
     * @param email email of the account's user
     * @param name  name of the account's user
     * @throws SignInTokenDispatchException   if email cannot be sent (due to upstream service error).
     * @throws TooManySignInAttemptsException if auth user makes too many attempts without a successful sign-in.
     */
    @Transactional
    void signUp(@NonNull String email, @NonNull String name) throws TooManySignInAttemptsException {
        val user = authUserRepository.findActiveByEmail(email)
            .orElseGet(() -> authUserRepository.save(
                AuthUser.builder()
                    .email(email)
                    .name(name)
                    .build()));

        val refreshToken = createSignInToken(user);
        signInTokenDispatchStrategy.dispatch(refreshToken, email);
    }

    /**
     * Checks if an account with the given email exists and then sends a sign-in link to the email.
     *
     * @param email email of the account's user
     * @throws AccountNotFoundException       if an account with the given email doesn't exist.
     * @throws SignInTokenDispatchException   if email cannot be sent (due to upstream service error).
     * @throws TooManySignInAttemptsException if auth user makes too many attempts without a successful sign-in.
     */
    @Transactional
    void signIn(@NonNull String email) throws AccountNotFoundException, TooManySignInAttemptsException {
        val user = authUserRepository.findActiveByEmail(email)
            .orElseThrow(() -> {
                val msg = String.format("account with email '%s' doesn't exist", email);
                return new AccountNotFoundException(msg);
            });

        val refreshToken = createSignInToken(user);
        signInTokenDispatchStrategy.dispatch(refreshToken, email);
    }

    @NonNull
    @Transactional
    private String createSignInToken(@NonNull AuthUser authUser) throws TooManySignInAttemptsException {
        if (authUser.getSignInAttempts() >= MAX_SIGN_IN_ATTEMPTS_PER_USER) {
            throw new TooManySignInAttemptsException(authUser.getEmail());
        }

        authUser.incrementSignInAttempts();
        authUserRepository.save(authUser);
        return refreshTokenRepository.save(
                RefreshToken.builder()
                    .owner(authUser)
                    .expiresAt(LocalDateTime.now().plus(authConfig.getSignInTokenExpiry()))
                    .build())
            .getJwt(jwtAlgorithm);
    }

    /**
     * Revokes a valid refresh token.
     *
     * @param refreshToken refresh token provided by the client
     * @throws RefreshTokenVerificationException if the refresh token is invalid, expired or re-used.
     */
    void signOut(@NonNull String refreshToken) throws RefreshTokenVerificationException {
        val token = verifyRefreshJWT(refreshToken);
        refreshTokenRepository.delete(token);
    }

    /**
     * Verifies if the given refresh token is valid and then returns a fresh set of auth
     * credentials.
     *
     * @param refreshToken refresh token provided by the client
     * @param userAgent    user-agent that the client used to make this request. It can be {@code null}.
     * @return fresh {@link AuthCredentialsResponse}
     * @throws RefreshTokenVerificationException if the refresh token is invalid, expired or re-used.
     */
    @NonNull
    @Transactional
    AuthCredentialsResponse issueAuthCredentials(@NonNull String refreshToken, String userAgent) throws RefreshTokenVerificationException {
        var token = verifyRefreshJWT(refreshToken);

        // version 0 implies that this refresh token is being used to sign in, so persist userAgent.
        if (token.getVersion() == 0) {
            token.setUserAgent(userAgent);
        }

        token.setExpiresAt(LocalDateTime.now().plus(authConfig.getRefreshTokenExpiry()));
        token = refreshTokenRepository.save(token);

        // reset sign in attempts and update last active timestamp for the auth user. Last active
        // timestamp uses pre-update hook on the AuthUser entity.
        token.getOwner().resetSignInAttempts();
        authUserRepository.save(token.getOwner());

        val accessTokenExpiry = LocalDateTime.now().plus(authConfig.getAccessTokenExpiry());
        val signedAccessToken = JWT.create()
            .withSubject("" + token.getOwner().getId())
            .withExpiresAt(Date.from(accessTokenExpiry.atZone(ZoneId.systemDefault()).toInstant()))
            .sign(jwtAlgorithm);

        return AuthCredentialsResponse.builder()
            .refreshToken(token.getJwt(jwtAlgorithm))
            .accessToken(signedAccessToken)
            .build();
    }

    @NonNull
    private RefreshToken verifyRefreshJWT(@NonNull String jwt) throws RefreshTokenVerificationException {
        final long jwtId, jwtVersion;
        try {
            val decodedToken = jwtVerifier.verify(jwt);
            jwtId = parseLong(decodedToken.getId());
            jwtVersion = decodedToken.getClaim(RefreshToken.ORD_JWT_CLAIM).asLong();
        } catch (JWTVerificationException e) {
            throw new RefreshTokenVerificationException("refresh token verification failed", e);
        }

        val token = refreshTokenRepository.findActiveById(jwtId)
            .orElseThrow(() -> new RefreshTokenVerificationException("refresh token doesn't exist in database"));

        // if token version is different, it implies that an old refresh token is being re-used.
        // delete token on re-use to effectively sign out both the legitimate user and the attacker.
        if (jwtVersion != token.getVersion()) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenVerificationException("refresh token version mismatch");
        }

        return token;
    }

    /**
     * Deletes the refresh token with given {@code tokenId} if the given 'tokenOwner' actually owns
     * it.
     *
     * @param tokenOwner expected owner of the token with {@code tokenId}.
     * @param tokenId    id of the refresh token to revoke.
     * @throws RefreshTokenRevokeException if such a token doesn't exist.
     */
    void revokeRefreshToken(@NonNull AuthUser tokenOwner, @NonNull Long tokenId) throws RefreshTokenRevokeException {
        val refreshToken = refreshTokenRepository.findActiveById(tokenId)
            .orElseThrow(() -> {
                val errMsg = String.format("refresh token with id '%d' doesn't exist", tokenId);
                return new RefreshTokenRevokeException(errMsg);
            });

        if (!tokenOwner.getId().equals(refreshToken.getOwner().getId())) {
            val errMsg = "given 'tokenOwner' doesn't own a refresh token with the given id";
            throw new RefreshTokenRevokeException(errMsg);
        }

        refreshTokenRepository.delete(refreshToken);
    }

    /**
     * Returns an externalised view of an account's data, containing fields that are accessible by
     * account owners.
     *
     * @return a non-null {@link ProfileResponse}.
     */
    @NonNull
    ProfileResponse getProfile(@NonNull AuthUser authUser) {
        return ProfileResponse.builder()
            .accountId(authUser.getId())
            .name(authUser.getName())
            .email(authUser.getEmail())
            .activeSessions(
                refreshTokenRepository.findAllActiveByOwner(authUser)
                    .stream()
                    .map((token) -> ProfileResponse.ActiveSessionInfo.builder()
                        .refreshTokenId(token.getId())
                        .userAgent(token.getUserAgent())
                        .createdAt(token.getCreatedAt())
                        .lastUsedAt(token.getLastUsedAt())
                        .build())
                    .collect(Collectors.toUnmodifiableList()))
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
        try {
            val decodedToken = jwtVerifier.verify(token);
            return new BearerJWT(decodedToken);
        } catch (JWTVerificationException e) {
            log.trace("access token verification failed", e);
        }

        return null;
    }

    private class BearerJWT extends AbstractAuthenticationToken {

        private final DecodedJWT token;
        private final Long principalId;

        private BearerJWT(@NonNull DecodedJWT token) {
            super(List.of());
            this.token = token;

            try {
                this.principalId = parseLong(token.getSubject());
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
            return token != null && principalId != null;
        }
    }

    private static class BearerJwtAuthenticationException extends AuthenticationException {

        private BearerJwtAuthenticationException(String msg, Throwable cause) {
            super(msg, cause);
        }

        private BearerJwtAuthenticationException(String msg) {
            super(msg);
        }
    }
}
