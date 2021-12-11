package com.trynoice.api.subscription;


import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.NonNull;

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
     * @param customerEmail     customer's email.
     * @return a new checkout session
     * @throws StripeException on api call error
     */
    @NonNull
    Session createCheckoutSession(
        @NonNull String successUrl,
        @NonNull String cancelUrl,
        @NonNull String priceId,
        @NonNull String clientReferenceId,
        @NonNull String customerEmail
    ) throws StripeException {
        return Session.create(
            new SessionCreateParams.Builder()
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(clientReferenceId)
                .setCustomerEmail(customerEmail)
                .addLineItem(
                    new SessionCreateParams.LineItem.Builder()
                        .setQuantity(1L)
                        .setPrice(priceId)
                        .build())
                .build());
    }
}
