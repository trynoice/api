package com.trynoice.api.subscription.upstream;


import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.common.EmptyParam;
import lombok.NonNull;
import lombok.val;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

/**
 * A thin wrapper around {@link Stripe} api to enable easy mocking.
 */
public class StripeApi {

    private final StripeClient client;

    public StripeApi(@NonNull String apiKey) {
        client = new StripeClient(apiKey);
    }

    /**
     * Creates a Stripe checkout session in a fixed-price, single-user {@link
     * SessionCreateParams.Mode#SUBSCRIPTION subscription} mode.
     *
     * @param successUrl        url where user will be redirected after a successful checkout.
     * @param cancelUrl         url where user will be redirected on cancelling the checkout.
     * @param priceId           price id of the subscription.
     * @param expireAfter       duration in range 30 minutes and 24 hours after which the checkout
     *                          session expires.
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
    public Session createCheckoutSession(
        @NonNull String successUrl,
        @NonNull String cancelUrl,
        @NonNull String priceId,
        @NonNull Duration expireAfter,
        @NonNull String clientReferenceId,
        String customerEmail,
        String stripeCustomerId,
        Long trialPeriodDays
    ) throws StripeException {
        return client.checkout().sessions().create(
            new SessionCreateParams.Builder()
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setExpiresAt(OffsetDateTime.now().plus(expireAfter).toEpochSecond())
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
     * @see StripeClient#constructEvent(String, String, String)
     */
    @NonNull
    public Event decodeWebhookPayload(
        @NonNull String payload,
        @NonNull String signature,
        @NonNull String secret
    ) throws SignatureVerificationException {
        return client.constructEvent(payload, signature, secret);
    }

    /**
     * @see com.stripe.service.SubscriptionService#retrieve(String)
     */
    @NonNull
    public Subscription getSubscription(@NonNull String id) throws StripeException {
        return client.subscriptions().retrieve(id);
    }

    /**
     * Marks an uncancelled subscription to be cancelled at the end of the current billing cycle.
     *
     * @param id id of the subscription to cancel.
     * @throws StripeException on Stripe API errors.
     * @see com.stripe.service.SubscriptionService#update(String, SubscriptionUpdateParams)
     * @see com.stripe.service.SubscriptionService#cancel(String, SubscriptionCancelParams)
     */
    public void cancelSubscription(@NonNull String id) throws StripeException {
        val subscription = getSubscription(id);
        if ("canceled".equals(subscription.getStatus())) {
            return;
        }

        if (!requireNonNullElse(subscription.getCancelAtPeriodEnd(), false)) {
            client.subscriptions()
                .update(
                    id,
                    SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build());
        }
    }

    /**
     * Immediately cancel a subscription and refund its payment.
     *
     * @param id id of the stripe subscription.
     * @throws StripeException on Stripe API errors.
     */
    public void refundSubscription(@NonNull String id) throws StripeException {
        val subscription = client.subscriptions().retrieve(
            id,
            SubscriptionRetrieveParams.builder()
                .addExpand("latest_invoice")
                .build(),
            null);

        // charge may be null if the latest invoice is for a trial period.
        val charge = Optional.ofNullable(subscription.getLatestInvoiceObject())
            .map(Invoice::getCharge)
            .orElse(null);

        if (charge != null) {
            try {
                client.refunds().create(
                    RefundCreateParams.builder()
                        .setCharge(charge)
                        .build());
            } catch (StripeException e) {
                if (!"charge_already_refunded".equals(e.getCode())) {
                    throw e;
                }
            }
        }

        if (!"canceled".equals(subscription.getStatus())) {
            client.subscriptions().cancel(id);
        }
    }

    /**
     * @see com.stripe.service.billingportal.SessionService#create(com.stripe.param.billingportal.SessionCreateParams)
     */
    @NonNull
    public com.stripe.model.billingportal.Session createCustomerPortalSession(
        @NonNull String customerId,
        String returnUrl
    ) throws StripeException {
        return client.billingPortal().sessions().create(
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build());
    }

    /**
     * Sets the name and email of a Stripe Customer with given {@code customerId} to an empty
     * string.
     *
     * @param customerId a not {@literal null} customer id recognised by Stripe API.
     * @throws StripeException on upstream errors.
     */
    public void resetCustomerNameAndEmail(@NonNull String customerId) throws StripeException {
        client.customers().update(
            customerId,
            CustomerUpdateParams.builder()
                .setName(EmptyParam.EMPTY)
                .setEmail(EmptyParam.EMPTY)
                .build());
    }
}
