package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.models.AuthCredentials;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieAuthFilterTest {

    private static final String INVALID_JWT = "invalid-token";
    private static final String VALID_JWT = "valid-token";
    private static final String COOKIE_DOMAIN = "api.test";
    private static final Duration COOKIE_MAX_AGE = Duration.ofHours(1);

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Mock
    AuthConfiguration authConfiguration;

    @Mock
    private AccountService accountService;

    private CookieAuthFilter filter;

    @BeforeEach
    void setUp() throws RefreshTokenVerificationException {
        this.filter = new CookieAuthFilter(this.authConfiguration, this.accountService);
        SecurityContextHolder.clearContext();

        lenient().when(authConfiguration.getCookieDomain()).thenReturn(COOKIE_DOMAIN);
        lenient().when(authConfiguration.getRefreshTokenExpiry()).thenReturn(COOKIE_MAX_AGE);
        lenient().when(authConfiguration.getAccessTokenExpiry()).thenReturn(COOKIE_MAX_AGE);

        lenient().when(accountService.verifyAccessToken(INVALID_JWT)).thenReturn(null);
        lenient().when(accountService.verifyAccessToken(VALID_JWT)).thenReturn(mock(Authentication.class));

        lenient().when(accountService.issueAuthCredentials(eq(VALID_JWT), any()))
            .thenThrow(new RefreshTokenVerificationException("test-error"));

        lenient().when(accountService.issueAuthCredentials(eq(VALID_JWT), any()))
            .thenReturn(new AuthCredentials(VALID_JWT, VALID_JWT));
    }

    @AfterEach
    void verifyFilterChainInvocation() throws ServletException, IOException {
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withExistingAuthentication() throws ServletException, IOException {
        val authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filter.doFilterInternal(request, response, filterChain);
        verify(request, times(0)).getCookies();
        assertEquals(authentication, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withoutAuthTokens() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withValidAccessToken() throws ServletException, IOException {
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, VALID_JWT),
        });

        filter.doFilterInternal(request, response, filterChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withInvalidAccessTokenAndInvalidRefreshToken() throws ServletException, IOException {
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, INVALID_JWT),
            new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, INVALID_JWT),
        });

        filter.doFilterInternal(request, response, filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withInvalidAccessTokenAndValidRefreshToken() throws ServletException, IOException {
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieAuthFilter.ACCESS_TOKEN_COOKIE, INVALID_JWT),
            new Cookie(CookieAuthFilter.REFRESH_TOKEN_COOKIE, VALID_JWT),
        });

        filter.doFilterInternal(request, response, filterChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        val cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response, times(2)).addCookie(cookieCaptor.capture());
        assertEquals(2, cookieCaptor.getAllValues().size());
        cookieCaptor.getAllValues().forEach((c) -> {
            assertEquals(VALID_JWT, c.getValue());
            assertEquals(COOKIE_MAX_AGE.toSeconds(), c.getMaxAge());
            assertEquals(COOKIE_DOMAIN, c.getDomain());
            assertTrue(c.isHttpOnly());
            assertTrue(c.getSecure());
        });
    }
}
