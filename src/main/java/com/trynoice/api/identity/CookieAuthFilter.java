package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.payload.AuthCredentialsResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

import static java.util.Objects.requireNonNullElse;

/**
 * <p>
 * {@link CookieAuthFilter} is a {@link OncePerRequestFilter OncePerRequest} security filter to
 * validate access JWTs and issue new credentials (access + refresh JWTs) using refresh JWTs. If a
 * previous filter set an authentication on the current security context, the filter takes no
 * further action. </p>
 * <p>
 * If a access token in {@link CookieAuthFilter#ACCESS_TOKEN_COOKIE} is valid, the request is
 * authenticated and no further action is performed. If the access token is missing, invalid or
 * expired, the filter looks for the {@link CookieAuthFilter#REFRESH_TOKEN_COOKIE}. If a valid
 * refresh token is found, the filter uses it to issue new auth credentials (access + refresh
 * tokens) and adds as new cookies to the {@link HttpServletResponse}. </p>
 */
@Slf4j
@Component
public class CookieAuthFilter extends OncePerRequestFilter {

    public static final String REFRESH_TOKEN_COOKIE = "rtc";
    public static final String ACCESS_TOKEN_COOKIE = "atc";

    private final AuthConfiguration authConfig;
    private final AccountService accountService;

    @Autowired
    CookieAuthFilter(@NonNull AuthConfiguration authConfig, @NonNull AccountService accountService) {
        this.authConfig = authConfig;
        this.accountService = accountService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            performCookieAuthentication(request, response);
        }

        filterChain.doFilter(request, response);
    }

    private void performCookieAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response) {
        if (request.getCookies() == null) {
            return;
        }

        String accessToken = null, refreshToken = null;
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                refreshToken = cookie.getValue();
            }

            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                accessToken = cookie.getValue();
            }
        }

        Authentication authentication = null;
        if (accessToken != null) {
            authentication = accountService.verifyAccessToken(accessToken);
        }

        if (authentication == null) {
            val userAgent = requireNonNullElse(request.getHeader(AccountController.USER_AGENT_HEADER), "");
            val credentials = issueCredentials(refreshToken, userAgent);
            if (credentials != null) {
                // to maintain consistency.
                authentication = accountService.verifyAccessToken(credentials.getAccessToken());

                // rotate credential cookies for the client.
                response.addCookie(
                    createCookie(
                        REFRESH_TOKEN_COOKIE,
                        credentials.getRefreshToken(),
                        authConfig.getRefreshTokenExpiry()));

                response.addCookie(
                    createCookie(
                        ACCESS_TOKEN_COOKIE,
                        credentials.getAccessToken(),
                        authConfig.getAccessTokenExpiry()));
            }
        }

        val context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private AuthCredentialsResponse issueCredentials(String refreshToken, String userAgent) {
        if (refreshToken == null) {
            return null;
        }

        try {
            return accountService.issueAuthCredentials(refreshToken, userAgent);
        } catch (RefreshTokenVerificationException e) {
            log.trace("refresh token verification failed", e);
            return null;
        }
    }

    private Cookie createCookie(@NonNull String key, @NonNull String value, @NonNull Duration maxAge) {
        return new Cookie(key, value) {{
            setMaxAge(Math.toIntExact(maxAge.toSeconds()));
            setSecure(true);
            setHttpOnly(true);
            setDomain(authConfig.getCookieDomain());
        }};
    }
}
