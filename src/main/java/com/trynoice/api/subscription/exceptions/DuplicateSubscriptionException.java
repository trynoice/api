package com.trynoice.api.subscription.exceptions;

/**
 * Thrown by createSubscription operation in SubscriptionService if the requesting user already has
 * an active subscription.
 */
public class DuplicateSubscriptionException extends Exception {
}
