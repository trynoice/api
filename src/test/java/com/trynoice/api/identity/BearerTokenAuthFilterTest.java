package com.trynoice.api.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerTokenAuthFilterTest {

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

    private BearerTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        this.filter = new BearerTokenAuthFilter(this.accountService);
        SecurityContextHolder.clearContext();

        lenient().when(accountService.verifyAccessToken(INVALID_JWT)).thenReturn(null);
        lenient().when(accountService.verifyAccessToken(VALID_JWT)).thenReturn(mock(Authentication.class));
    }

    @AfterEach
    void verifyFilterChainInvocation() throws ServletException, IOException {
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withExistingAuthentication() throws ServletException, IOException {
        val authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        lenient().when(request.getHeader(any())).thenReturn("bearer " + VALID_JWT);
        filter.doFilterInternal(request, response, filterChain);
        assertEquals(authentication, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withoutAuthorizationHeader() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withInvalidJWT() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn("bearer " + INVALID_JWT);
        filter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withValidJWT() throws ServletException, IOException {
        when(request.getHeader(any())).thenReturn("bearer " + VALID_JWT);
        filter.doFilterInternal(request, response, filterChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
