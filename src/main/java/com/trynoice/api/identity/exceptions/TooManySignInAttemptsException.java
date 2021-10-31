package com.trynoice.api.identity.exceptions;

import static java.lang.String.format;

/**
 * Thrown by signIn and signUp operations in {@link com.trynoice.api.identity.AccountService}.
 */
public class TooManySignInAttemptsException extends Exception {

    public TooManySignInAttemptsException(String email) {
        super(format("auth user with '%s' made too many attempts without a successful sign-in", email));
    }
}
