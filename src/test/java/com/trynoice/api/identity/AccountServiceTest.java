package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.AuthUser;
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
class AccountServiceTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthConfiguration authConfiguration;

    @Mock
    private SignInTokenDispatchStrategy signInTokenDispatchStrategy;

    private AccountService service;

    @BeforeEach
    void setUp() {
        when(authConfiguration.getHmacSecret()).thenReturn(TEST_HMAC_SECRET);
        this.service = new AccountService(authUserRepository, refreshTokenRepository, authConfiguration, signInTokenDispatchStrategy);
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
