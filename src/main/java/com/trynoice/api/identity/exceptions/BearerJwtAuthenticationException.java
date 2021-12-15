package com.trynoice.api.identity.exceptions;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by BearerJwt in the AccountService for invalid bearer access tokens.
 */
public class BearerJwtAuthenticationException extends AuthenticationException {

    public BearerJwtAuthenticationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public BearerJwtAuthenticationException(String msg) {
        super(msg);
    }
}
