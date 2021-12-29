package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by cancelSubscription operation in SubscriptionService.
 */
public class SubscriptionNotFoundException extends Exception {

    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}
