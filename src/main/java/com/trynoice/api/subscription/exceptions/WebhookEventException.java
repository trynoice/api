package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by webhook event handlers when the event could not be processed correctly.
 */
public class WebhookEventException extends Exception {

    public WebhookEventException(String message) {
        super(message);
    }

    public WebhookEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
