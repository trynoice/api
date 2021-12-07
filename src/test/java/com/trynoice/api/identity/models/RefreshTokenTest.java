package com.trynoice.api.identity.models;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RefreshTokenTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Test
    void getSignedJwt() {
        val jwtAlgorithm = Algorithm.HMAC256(TEST_HMAC_SECRET);
        val refreshToken = RefreshToken.builder()
            .owner(mock(AuthUser.class))
            .expiresAt(LocalDateTime.now().plus(Duration.ofHours(1)))
            .build();

        refreshToken.setId(1L);
        refreshToken.setVersion(1L);
        refreshToken.setCreatedAt(LocalDateTime.now());

        val signedToken = refreshToken.getJwt(jwtAlgorithm);
        val decodedJwt = JWT.require(jwtAlgorithm).build().verify(signedToken);
        assertEquals("1", decodedJwt.getId());
    }
}
