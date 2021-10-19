package com.trynoice.api.identity;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>
 * A {@link OncePerRequestFilter OncePerRequest} security filter to validate <i>bearer</i> JSON web
 * token passed using the <i>Authorization</i> request header. </p>
 * <p>
 * If the Authorization header is invalid or not provided, the filter leaves the existing {@link
 * org.springframework.security.core.context.SecurityContext SecurityContext} untouched. However, if
 * the authorization header is valid, the filter sets a new {@link
 * org.springframework.security.core.context.SecurityContext SecurityContext} on the {@link
 * SecurityContextHolder}. </p>
 * <p>
 * If the JWT in the authorization header is valid, it sets a non-null {@link
 * org.springframework.security.core.Authentication Authentication} on the {@link
 * org.springframework.security.core.context.SecurityContext SecurityContext}. Otherwise, it sets a
 * {@code null} {@link org.springframework.security.core.Authentication Authentication} on the
 * {@link org.springframework.security.core.context.SecurityContext SecurityContext}. </p>
 */
@Component
@Slf4j
public class AuthBearerJWTReadFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Autowired
    public AuthBearerJWTReadFilter(@NonNull AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        val header = request.getHeader("authorization");
        if (header != null && header.toLowerCase().startsWith("bearer ")) {
            val context = SecurityContextHolder.createEmptyContext();
            val authentication = authService.verifyBearerJWT(header.substring(7));
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }
}
