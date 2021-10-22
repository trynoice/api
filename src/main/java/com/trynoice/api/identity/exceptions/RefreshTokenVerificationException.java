package com.trynoice.api.identity.exceptions;

/**
 * Thrown by issueAuthCredentials operation in the AccountService.
 */
public class RefreshTokenVerificationException extends Exception {

    public RefreshTokenVerificationException(String message) {
        super(message);
    }

    public RefreshTokenVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
