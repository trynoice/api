package com.trynoice.api.testing;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.models.RefreshToken;
import lombok.NonNull;
import lombok.val;

import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A collection of utility (static) methods to test auth scenarios.
 */
public class AuthTestUtils {

    /**
     * Creates a new {@link AuthUser} with its name set to a fresh {@link UUID} and email set to
     * {@code <uuid>@test}.
     *
     * @param entityManager to persist the {@link AuthUser} entity to the database.
     * @return a non-null {@link AuthUser}.
     */
    @NonNull
    public static AuthUser createAuthUser(@NonNull EntityManager entityManager) {
        val uuid = UUID.randomUUID().toString();
        val authUser = AuthUser.builder()
            .name(uuid)
            .email(uuid + "@api.test")
            .build();

        entityManager.persist(authUser);
        return authUser;
    }

    /**
     * Creates a fresh {@link RefreshToken} for the provided {@link AuthUser} and returns it as a
     * String. The returned string is a signed JWT string if {@link JwtType} is {@link JwtType#VALID
     * VALID}, {@link JwtType#EXPIRED EXPIRED} and {@link JwtType#REUSED REUSED}.
     *
     * @param entityManager to persist the {@link RefreshToken} entity to the database.
     * @param hmacSecret    to sign the JWT.
     * @param owner         owner of the created {@link RefreshToken}.
     * @param type          controls the behaviour of the signed JWT.
     * @return a {@code nullable} {@link String}
     */
    public static String createRefreshToken(
        @NonNull EntityManager entityManager,
        @NonNull String hmacSecret,
        @NonNull AuthUser owner,
        @NonNull JwtType type
    ) {
        var expiresAt = LocalDateTime.now().plus(Duration.ofHours(1));
        switch (type) {
            case NULL:
                return null;
            case EMPTY:
                return "";
            case INVALID:
                return "invalid-token";
            case EXPIRED:
                expiresAt = LocalDateTime.now().minus(Duration.ofHours(1));
                break;
        }

        val refreshToken = RefreshToken.builder()
            .expiresAt(expiresAt)
            .userAgent("")
            .owner(owner)
            .build();

        entityManager.persist(refreshToken);
        refreshToken.setVersion(refreshToken.getVersion() - (type == JwtType.REUSED ? 1 : 0));
        return refreshToken.getJwt(Algorithm.HMAC256(hmacSecret));
    }

    /**
     * Creates a fresh access token for the given {@link AuthUser} and returns it as a string. The
     * returned string is a signed JWT string if {@link JwtType} is {@link JwtType#VALID VALID} or
     * {@link JwtType#EXPIRED EXPIRED}. <b>The behaviour for {@link JwtType#REUSED} is
     * undefined. Please note that it doesn't create a refresh token.</b>
     *
     * @param hmacSecret secret to sign the JWT.
     * @param authUser   subject of the access token.
     * @param type       controls the behaviour of the signed JWT.
     * @return a {@code nullable} {@link String}.
     */
    public static String createAccessToken(
        @NonNull String hmacSecret,
        @NonNull AuthUser authUser,
        @NonNull JwtType type
    ) {
        var expiresAt = Calendar.getInstance();
        switch (type) {
            case NULL:
                return null;
            case EMPTY:
                return "";
            case INVALID:
                return "invalid-token";
            case EXPIRED:
                expiresAt.add(Calendar.HOUR, -1);
                break;
            case VALID:
                expiresAt.add(Calendar.HOUR, 1);
                break;
        }

        return JWT.create()
            .withSubject("" + authUser.getId())
            .withExpiresAt(expiresAt.getTime())
            .sign(Algorithm.HMAC256(hmacSecret));
    }

    /**
     * Asserts if the given token was signed by the given HMAC secret.
     *
     * @param hmacSecret secret that was used to sign the token.
     * @param token      JWT to verify.
     */
    public static void assertValidJWT(@NonNull String hmacSecret, String token) {
        assertNotNull(token);
        assertDoesNotThrow(() ->
            JWT.require(Algorithm.HMAC256(hmacSecret))
                .build()
                .verify(token));
    }

    /**
     * Controls the behaviour of the signed JWTs and {@link RefreshToken} entities created using
     * utility methods in {@link AuthTestUtils}. <b>{@link JwtType#REUSED} should be applied to
     * refresh tokens only.</b>
     */
    public enum JwtType {
        NULL,
        EMPTY,
        INVALID,
        EXPIRED,
        REUSED,
        VALID
    }
}
