package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Mock
    private AuthUserRepository authUserRepository;

    private AuthService service;

    @BeforeEach
    void setUp() {
        this.service = new AuthService(authUserRepository, TEST_HMAC_SECRET);
    }

    @Test
    void verifyBearerJWT() {
        val invalidToken = "invalid-token";
        assertNull(service.verifyBearerJWT(invalidToken));

        val principalId = 0;
        val validToken = JWT.create()
            .withSubject("" + principalId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(authUserRepository.findActiveById(principalId))
            .thenReturn(Optional.of(mock(AuthUser.class)));

        val auth = service.verifyBearerJWT(validToken);
        assertNotNull(auth);
        assertNotNull(auth.getPrincipal());
        verify(authUserRepository, times(1)).findActiveById(principalId);
    }
}
