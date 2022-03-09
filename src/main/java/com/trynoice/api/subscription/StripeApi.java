package com.trynoice.api.subscription;


import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.NonNull;
import lombok.val;

import static java.util.Objects.requireNonNullElse;

/**
 * A thin wrapper around {@link Stripe} api to enable easy mocking.
 */
public class StripeApi {

    public StripeApi(@NonNull String apiKey) {
        Stripe.apiKey = apiKey;
    }

    /**
     * Creates a Stripe checkout session in a fixed-price, single-user {@link
     * SessionCreateParams.Mode#SUBSCRIPTION subscription} mode.
     *
     * @param successUrl        url where user will be redirected after a successful checkout.
     * @param cancelUrl         url where user will be redirected on cancelling the checkout.
     * @param priceId           price id of the subscription.
     * @param clientReferenceId a reference id to identify customer internally.
     * @param customerEmail     customer's email. If it and {@code stripeCustomerId} are both
     *                          {@literal null}, Stripe will explicitly ask for it during the
     *                          checkout. It must not be specified with a not {@literal null}
     *                          {@code stripeCustomerId}.
     * @param stripeCustomerId  customer id assigned by Stripe to the subscription owner. If it is
     *                          {@literal null}, Stripe will create a new Customer entity. If
     *                          {@code customerEmail} must be {@literal null} if it is specified.
     * @param trialPeriodDays   the number of days for offering a trial period on the new
     *                          subscription. If it is {@literal null}, no trial period is offered.
     *                          If it is not {@literal null}, it has to be at least 1.
     * @return a new checkout session
     * @throws StripeException on api call error
     */
    @NonNull
    Session createCheckoutSession(
        @NonNull String successUrl,
        @NonNull String cancelUrl,
        @NonNull String priceId,
        @NonNull String clientReferenceId,
        String customerEmail,
        String stripeCustomerId,
        Long trialPeriodDays
    ) throws StripeException {
        return Session.create(
            new SessionCreateParams.Builder()
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(clientReferenceId)
                .setCustomerEmail(customerEmail)
                .setCustomer(stripeCustomerId)
                .setSubscriptionData(
                    trialPeriodDays == null ? null : SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(trialPeriodDays)
                        .build())
                .addLineItem(
                    new SessionCreateParams.LineItem.Builder()
                        .setQuantity(1L)
                        .setPrice(priceId)
                        .build())
                .build());
    }

    /**
     * @see Webhook#constructEvent(String, String, String)
     */
    @NonNull
    Event decodeWebhookPayload(
        @NonNull String payload,
        @NonNull String signature,
        @NonNull String secret
    ) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signature, secret);
    }

    /**
     * @see Subscription#retrieve(String)
     */
    @NonNull
    Subscription getSubscription(@NonNull String id) throws StripeException {
        return Subscription.retrieve(id);
    }

    /**
     * Marks an uncancelled subscription to be cancelled at the end of the current billing cycle.
     *
     * @see Subscription#update(SubscriptionUpdateParams)
     */
    void cancelSubscription(@NonNull String id) throws StripeException {
        val subscription = getSubscription(id);
        val isCancelled = "canceled".equals(subscription.getStatus());
        val isCancellingAtPeriodEnd = requireNonNullElse(subscription.getCancelAtPeriodEnd(), false);
        if (isCancelled || isCancellingAtPeriodEnd) {
            return;
        }

        // cancel at the end of current billing period to match Google Play Subscriptions behaviour.
        subscription.update(
            SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build());
    }

    /**
     * @see com.stripe.model.billingportal.Session#create(com.stripe.param.billingportal.SessionCreateParams)
     */
    @NonNull
    com.stripe.model.billingportal.Session createCustomerPortalSession(
        @NonNull String customerId,
        String returnUrl
    ) throws StripeException {
        return com.stripe.model.billingportal.Session.create(
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build());
    }
}
