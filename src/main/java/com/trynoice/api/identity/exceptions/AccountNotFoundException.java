package com.trynoice.api.identity.exceptions;

import static java.lang.String.format;

/**
 * Thrown by signIn operation in the AccountService when the account for given look-up isn't found.
 */
public class AccountNotFoundException extends Exception {

    public AccountNotFoundException(String lookupKey, Object lookupValue) {
        super(format("auth user with %s doesn't exist: %s", lookupKey, lookupValue));
    }
}
