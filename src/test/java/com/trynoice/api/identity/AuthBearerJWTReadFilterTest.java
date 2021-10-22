package com.trynoice.api.identity;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthBearerJWTReadFilterTest {

    private static final String INVALID_JWT = "invalid-token";
    private static final String VALID_JWT = "valid-token";

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Mock
    private AccountService accountService;

    private AuthBearerJWTReadFilter filter;

    @BeforeEach
    void setUp() {
        this.filter = new AuthBearerJWTReadFilter(this.accountService);
        SecurityContextHolder.clearContext();

        Mockito.lenient()
            .when(accountService.verifyBearerJWT(INVALID_JWT))
            .thenReturn(null);

        Mockito.lenient()
            .when(accountService.verifyBearerJWT(VALID_JWT))
            .thenReturn(mock(Authentication.class));
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
        when(request.getHeader(any())).thenReturn("bearer " + INVALID_JWT);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        val context = SecurityContextHolder.getContext();
        if (context != null) {
            assertNull(context.getAuthentication());
        }
    }

    @Test
    void doFilterInternal_withValidJWT() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn("bearer " + VALID_JWT);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);

        val context = SecurityContextHolder.getContext();
        assertNotNull(context);
        assertNotNull(context.getAuthentication());
    }
}
