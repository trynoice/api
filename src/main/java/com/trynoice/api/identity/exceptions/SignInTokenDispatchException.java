package com.trynoice.api.identity.exceptions;

/**
 * Thrown by {@link com.trynoice.api.identity.SignInTokenDispatchStrategy}.
 */
public class SignInTokenDispatchException extends RuntimeException {

    public SignInTokenDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
