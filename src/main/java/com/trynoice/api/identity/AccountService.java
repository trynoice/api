package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.trynoice.api.identity.exceptions.AuthUserNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationFailed;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.models.RefreshToken;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * {@link AccountService} implements operations related to account management and auth.
 */
@Service
@Slf4j
class AccountService {

    private static final String REFRESH_TOKEN_ORDINAL_CLAIM = "ord";

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
     * @throws SignInTokenDispatchException if email cannot be sent (due to upstream service error).
     */
    @Transactional
    void signUp(@NonNull String email, @NonNull String name) throws SignInTokenDispatchException {
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
     * @throws AuthUserNotFoundException    if an account with the given email doesn't exist.
     * @throws SignInTokenDispatchException if email cannot be sent (due to upstream service error).
     */
    void signIn(@NonNull String email) throws AuthUserNotFoundException, SignInTokenDispatchException {
        val user = authUserRepository.findActiveByEmail(email)
            .orElseThrow(() -> new AuthUserNotFoundException("email", email));

        val refreshToken = createSignInToken(user);
        signInTokenDispatchStrategy.dispatch(refreshToken, email);
    }

    @NonNull
    @Transactional
    private String createSignInToken(@NonNull AuthUser authUser) {
        val refreshToken = refreshTokenRepository.save(
            RefreshToken.builder()
                .owner(authUser)
                .expiresAt(LocalDateTime.now().plus(authConfig.getSignInTokenExpiry()))
                .build());

        return createSignedRefreshTokenString(refreshToken);
    }

    @NonNull
    private String createSignedRefreshTokenString(@NonNull RefreshToken token) {
        return JWT.create()
            .withJWTId("" + token.getId())
            .withClaim(REFRESH_TOKEN_ORDINAL_CLAIM, token.getVersion())
            .withExpiresAt(Date.from(token.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant()))
            .sign(jwtAlgorithm);
    }

    /**
     * Verifies if the given refresh token is valid and then returns a fresh set of auth
     * credentials.
     *
     * @param refreshToken refresh token provided the user
     * @param userAgent    user-agent that user used to make this request
     * @return fresh {@link AuthCredentials}
     * @throws RefreshTokenVerificationFailed if the refresh token is invalid, expired or re-used.
     */
    @NonNull
    @Transactional
    AuthCredentials issueAuthCredentials(
        @NonNull String refreshToken,
        @NonNull String userAgent
    ) throws RefreshTokenVerificationFailed {
        final long tokenId, tokenVersion;
        try {
            val decodedToken = jwtVerifier.verify(refreshToken);
            tokenId = Long.parseLong(decodedToken.getId());
            tokenVersion = decodedToken.getClaim(REFRESH_TOKEN_ORDINAL_CLAIM).asLong();
        } catch (JWTVerificationException e) {
            throw new RefreshTokenVerificationFailed("refresh token verification failed", e);
        }

        var token = refreshTokenRepository.findActiveById(tokenId)
            .orElseThrow(() -> new RefreshTokenVerificationFailed("refresh token doesn't exist in database"));

        // if token version is different, it implies that an old refresh token is being re-used.
        // delete token on re-use to effectively sign out both the legitimate user and the attacker.
        if (tokenVersion != token.getVersion()) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenVerificationFailed("refresh token version mismatch");
        }

        // version 0 implies that this refresh token is being used to sign in, so persist userAgent.
        if (token.getVersion() == 0) {
            token.setUserAgent(userAgent);
        }

        token.setExpiresAt(LocalDateTime.now().plus(authConfig.getRefreshTokenExpiry()));
        token = refreshTokenRepository.save(token);

        val accessTokenExpiry = LocalDateTime.now().plus(authConfig.getAccessTokenExpiry());
        val signedAccessToken = JWT.create()
            .withSubject("" + token.getOwner().getId())
            .withExpiresAt(Date.from(accessTokenExpiry.atZone(ZoneId.systemDefault()).toInstant()))
            .sign(jwtAlgorithm);

        return AuthCredentials.builder()
            .refreshToken(createSignedRefreshTokenString(token))
            .accessToken(signedAccessToken)
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
    Authentication verifyBearerJWT(@NonNull String token) {
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
        private final Integer principalId;

        private BearerJWT(@NonNull DecodedJWT token) {
            super(List.of());
            this.token = token;

            Integer principalId;
            try {
                principalId = Integer.parseInt(token.getSubject());
            } catch (NumberFormatException e) {
                log.debug("failed to parse jwt subject", e);
                principalId = null;
            }

            this.principalId = principalId;
        }

        @Override
        public Object getCredentials() {
            return token;
        }

        @Override
        public Object getPrincipal() {
            if (principalId == null) {
                return null;
            }

            return authUserRepository.findActiveById(principalId).orElse(null);
        }

        @Override
        public boolean isAuthenticated() {
            return token != null && principalId != null;
        }
    }
}
