package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final JWTVerifier jwtVerifier;

    @Autowired
    public AuthService(
        @NonNull AuthUserRepository authUserRepository,
        @Value("${app.auth.hmac-secret}") String hmacSecret
    ) {
        this.authUserRepository = authUserRepository;
        this.jwtVerifier = JWT.require(Algorithm.HMAC256(hmacSecret)).build();
    }

    /**
     * Verifies if the provided JWT is valid. If the provided JWT is valid, clients can lazy-fetch
     * {@link AuthUser} using {@link Authentication#getPrincipal()}.
     *
     * @param token jwt to verify
     * @return a non-null {@link Authentication} if the provided token is valid. {@code null} if the
     * token is invalid.
     */
    public Authentication verifyBearerJWT(@NonNull final String token) {
        try {
            val decodedToken = jwtVerifier.verify(token);
            return new BearerJWT(decodedToken);
        } catch (JWTVerificationException e) {
            log.debug("jwt verification failed", e);
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
