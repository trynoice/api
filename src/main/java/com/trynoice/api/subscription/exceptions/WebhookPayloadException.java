package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by webhook event handlers when the event could not be parsed correctly.
 */
public class WebhookPayloadException extends Exception {

    public WebhookPayloadException(String message) {
        super(message);
    }

    public WebhookPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
