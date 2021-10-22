package com.trynoice.api.identity.exceptions;

/**
 * Thrown by issueAuthCredentials operation in the AccountService.
 */
public class RefreshTokenVerificationFailed extends Exception {

    public RefreshTokenVerificationFailed(String message) {
        super(message);
    }

    public RefreshTokenVerificationFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
