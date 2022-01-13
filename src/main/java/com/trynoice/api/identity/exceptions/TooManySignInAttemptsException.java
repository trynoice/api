package com.trynoice.api.identity.exceptions;

import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;

import static java.lang.String.format;

/**
 * Thrown by signIn and signUp operations in {@link com.trynoice.api.identity.AccountService}.
 */
@Getter
public class TooManySignInAttemptsException extends Exception {

    /**
     * Duration after which the account can retry this request.
     */
    private final Duration retryAfter;

    public TooManySignInAttemptsException(@NonNull String email, @NonNull Duration retryAfter) {
        super(format("auth user with '%s' made too many attempts without a successful sign-in", email));
        this.retryAfter = retryAfter;
    }
}
