package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by webhook event handlers in the subscription service.
 */
public class SubscriptionWebhookEventException extends Exception {

    public SubscriptionWebhookEventException(String message) {
        super(message);
    }

    public SubscriptionWebhookEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
