package com.trynoice.api.testing;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.RefreshToken;
import lombok.NonNull;
import lombok.val;

import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
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
        return entityManager.merge(AuthUser.builder()
            .name(uuid)
            .email(uuid + "@api.test")
            .build());
    }

    /**
     * Creates a new {@link RefreshToken} in the database with its expiry set to one hour from now.
     *
     * @param entityManager to persist the {@link RefreshToken} entity to the database.
     * @param owner         owner of the newly created {@link RefreshToken} entity.
     * @return the newly create {@link RefreshToken}.
     */
    @NonNull
    public static RefreshToken createRefreshToken(@NonNull EntityManager entityManager, @NonNull AuthUser owner) {
        return entityManager.merge(RefreshToken.builder()
            .expiresAt(OffsetDateTime.now().plus(Duration.ofHours(1)))
            .userAgent("")
            .owner(owner)
            .build());
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
    public static String createSignedRefreshJwt(
        @NonNull EntityManager entityManager,
        @NonNull String hmacSecret,
        @NonNull AuthUser owner,
        @NonNull JwtType type
    ) {
        var expiresAt = OffsetDateTime.now().plus(Duration.ofHours(1));
        switch (type) {
            case NULL:
                return null;
            case EMPTY:
                return "";
            case INVALID:
                return "invalid-token";
            case EXPIRED:
                expiresAt = OffsetDateTime.now().minus(Duration.ofHours(1));
                break;
        }

        val refreshToken = entityManager.merge(
            RefreshToken.builder()
                .expiresAt(expiresAt)
                .userAgent("")
                .owner(owner)
                .build());

        val jwtSigningAlgorithm = Algorithm.HMAC256(hmacSecret);
        if (type != JwtType.REUSED) {
            return refreshToken.toSignedJwt(jwtSigningAlgorithm);
        }

        // create a separate entity that is not attached to the entity manager.
        return RefreshToken.builder()
            .id(refreshToken.getId())
            .createdAt(refreshToken.getCreatedAt())
            .owner(owner)
            .expiresAt(refreshToken.getExpiresAt())
            .ordinal(refreshToken.getOrdinal() + 1)
            .build()
            .toSignedJwt(jwtSigningAlgorithm);
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
    public static String createSignedAccessJwt(
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
