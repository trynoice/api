package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by getPlans operation in the SubscriptionService when the given {@code provider} is
 * invalid.
 */
public class UnsupportedSubscriptionPlanProviderException extends Exception {

    public UnsupportedSubscriptionPlanProviderException(Throwable cause) {
        super(cause);
    }
}
