package com.trynoice.api.identity.exceptions;

/**
 * Thrown by signIn operation in the AccountService.
 */
public class AuthUserNotFoundException extends Exception {

    public AuthUserNotFoundException(String lookupKey, Object lookupValue) {
        super(String.format("auth user with %s doesn't exist: %s", lookupKey, lookupValue));
    }
}
