package com.trynoice.api.identity.exceptions;

/**
 * Thrown by revokeRefreshToken operation in the AccountService when the account for given look-up
 * isn't found.
 */
public class RefreshTokenRevokeException extends Exception {

    public RefreshTokenRevokeException(String message) {
        super(message);
    }
}
