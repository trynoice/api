package com.trynoice.api.identity.entities;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RefreshTokenTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Test
    void toSignedJwt() {
        val jwtAlgorithm = Algorithm.HMAC256(TEST_HMAC_SECRET);
        val signedToken = RefreshToken.builder()
            .id(1)
            .owner(mock(AuthUser.class))
            .expiresAt(OffsetDateTime.now().plus(Duration.ofHours(1)))
            .build()
            .toSignedJwt(jwtAlgorithm);

        val decodedJwt = JWT.require(jwtAlgorithm).build().verify(signedToken);
        assertEquals("1", decodedJwt.getId());
        assertEquals(0, decodedJwt.getClaim(RefreshToken.ORD_JWT_CLAIM).asLong());
    }
}
