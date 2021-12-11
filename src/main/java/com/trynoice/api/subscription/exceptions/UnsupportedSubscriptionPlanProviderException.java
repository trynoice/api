package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by operations in the SubscriptionService when a given {@code provider} is not supported by
 * the requested operation.
 */
public class UnsupportedSubscriptionPlanProviderException extends Exception {

    public UnsupportedSubscriptionPlanProviderException(String message) {
        super(message);
    }

    public UnsupportedSubscriptionPlanProviderException(Throwable cause) {
        super(cause);
    }
}
