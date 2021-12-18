package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by cancelSubscription operation in SubscriptionService.
 */
public class SubscriptionStateException extends Exception {

    public SubscriptionStateException(String message) {
        super(message);
    }
}
