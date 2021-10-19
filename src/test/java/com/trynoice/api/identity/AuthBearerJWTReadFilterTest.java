package com.trynoice.api.identity;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthBearerJWTReadFilterTest {

    private static final String TEST_HMAC_SECRET = "test-hmac-secret";

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Mock
    private AuthUserRepository authUserRepository;

    private AuthBearerJWTReadFilter filter;

    @BeforeEach
    void setUp() {
        val authService = new AuthService(authUserRepository, TEST_HMAC_SECRET);
        this.filter = new AuthBearerJWTReadFilter(authService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withoutAuthorizationHeader() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        val context = SecurityContextHolder.getContext();
        if (context != null) {
            assertNull(context.getAuthentication());
        }
    }

    @Test
    void doFilterInternal_withInvalidJWT() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn("bearer invalid-jwt");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        val context = SecurityContextHolder.getContext();
        if (context != null) {
            assertNull(context.getAuthentication());
        }
    }

    @Test
    void doFilterInternal_withValidJWT() throws ServletException, IOException {
        val subjectId = 0;
        val validToken = JWT.create()
            .withSubject("" + subjectId)
            .sign(Algorithm.HMAC256(TEST_HMAC_SECRET));

        when(request.getHeader(any())).thenReturn("bearer " + validToken);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        val context = SecurityContextHolder.getContext();
        assertNotNull(context);
        assertNotNull(context.getAuthentication());
    }
}
