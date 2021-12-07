package com.trynoice.api.identity.exceptions;

/**
 * Thrown by signIn operation in the AccountService when the account for given look-up isn't found.
 */
public class AccountNotFoundException extends Exception {

    public AccountNotFoundException(String msg) {
        super(msg);
    }
}
