package com.trynoice.api.identity;

import lombok.NonNull;
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
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private final AccountService accountService;

    @Autowired
    public BearerTokenAuthFilter(@NonNull AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        verifyAuthorizationHeader(request);
        filterChain.doFilter(request, response);
    }

    private void verifyAuthorizationHeader(@NonNull HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return; // a previous filter may have performed authentication.
        }

        val header = request.getHeader("authorization");
        if (header == null || !header.toLowerCase().startsWith("bearer ")) {
            return;
        }

        val context = SecurityContextHolder.createEmptyContext();
        val authentication = accountService.verifyBearerJWT(header.substring(7));
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
