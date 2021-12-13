package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.exception.StripeException;
import com.trynoice.api.identity.AccountService;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.platform.transaction.annotations.ReasonablyTransactional;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import lombok.NonNull;
import lombok.val;
import org.postgresql.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

/**
 * {@link AccountService} implements operations related to subscription management.
 */
@Service
class SubscriptionService {

    private final SubscriptionConfiguration subscriptionConfig;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final AndroidPublisherApi androidPublisherApi;
    private final StripeApi stripeApi;

    @Autowired
    SubscriptionService(
        @NonNull SubscriptionConfiguration subscriptionConfig,
        @NonNull SubscriptionPlanRepository subscriptionPlanRepository,
        @NonNull SubscriptionRepository subscriptionRepository,
        @NonNull ObjectMapper objectMapper,
        @NonNull AndroidPublisherApi androidPublisherApi,
        @NonNull StripeApi stripeApi
    ) {
        this.subscriptionConfig = subscriptionConfig;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.androidPublisherApi = androidPublisherApi;
        this.stripeApi = stripeApi;
    }

    /**
     * <p>
     * Fetch all available plans filtered by a {@code provider}.</p>
     *
     * <p>
     * If {@code provider} is {@code null}, it returns an unfiltered list of all available
     * subscription plans.</p>
     *
     * @param provider {@code null} or a valid {@link SubscriptionPlan.Provider}.
     * @return a non-null list of {@link SubscriptionPlanView}.
     * @throws UnsupportedSubscriptionPlanProviderException if an invalid {@code provider} is given.
     */
    @NonNull
    List<SubscriptionPlanView> getPlans(String provider) throws UnsupportedSubscriptionPlanProviderException {
        final List<SubscriptionPlan> plans;
        if (provider != null) {
            final SubscriptionPlan.Provider p;
            try {
                p = SubscriptionPlan.Provider.valueOf(provider);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedSubscriptionPlanProviderException(e);
            }

            plans = subscriptionPlanRepository.findAllActiveByProvider(p);
        } else {
            plans = subscriptionPlanRepository.findAllActive();
        }

        return plans.stream()
            .map(m -> SubscriptionPlanView.builder()
                .id(m.getId())
                .provider(m.getProvider().name())
                .providerPlanId(m.getProviderPlanId())
                .billingPeriodMonths(m.getBillingPeriodMonths())
                .priceInr(formatIndianPaiseToRupee(m.getPriceInIndianPaise()))
                .build())
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * <p>
     * Creates a new subscription entity in {@link Subscription.Status#CREATED} state and initiates
     * the subscription flow. Any given user can have at-most one subscription with non-{@link
     * Subscription.Status#INACTIVE INACTIVE} status.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#STRIPE}, it also
     * returns a non-null {@link SubscriptionFlowResult#getStripeCheckoutSessionUrl()}. The clients
     * must redirect user to the checkout session url to conclude the subscription flow.</p>
     *
     * @param requester user that initiated the subscription flow.
     * @param params    subscription flow parameters.
     * @return a non-null {@link SubscriptionFlowResult}.
     * @throws SubscriptionPlanNotFoundException if the specified plan doesn't exist.
     * @throws DuplicateSubscriptionException    if the user already has an active/pending subscription.
     */
    @NonNull
    @ReasonablyTransactional
    public SubscriptionFlowResult createSubscription(
        @NonNull AuthUser requester,
        @NonNull SubscriptionFlowParams params
    ) throws SubscriptionPlanNotFoundException, DuplicateSubscriptionException {
        val plan = subscriptionPlanRepository.findActiveById(params.getPlanId())
            .orElseThrow(SubscriptionPlanNotFoundException::new);

        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            if (params.getSuccessUrl() == null) {
                throw new ConstraintViolationException("'successUrl' is required for 'STRIPE' plans", null);
            }

            if (params.getCancelUrl() == null) {
                throw new ConstraintViolationException("'cancelUrl' is required for 'STRIPE' plans", null);
            }
        }

        val subscription = subscriptionRepository.findActiveByOwnerAndStatus(
            requester,
            Subscription.Status.CREATED,
            Subscription.Status.ACTIVE,
            Subscription.Status.PENDING);

        if (subscription.isPresent() && subscription.get().getStatus() != Subscription.Status.CREATED) {
            throw new DuplicateSubscriptionException();
        }

        val subscriptionId = subscription.orElseGet(
                () -> subscriptionRepository.save(
                    Subscription.builder()
                        .owner(requester)
                        .plan(plan)
                        .build()))
            .getId();

        val result = new SubscriptionFlowResult();
        result.setSubscriptionId(subscriptionId);
        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            try {
                result.setStripeCheckoutSessionUrl(stripeApi.createCheckoutSession(
                        params.getSuccessUrl(),
                        params.getCancelUrl(),
                        plan.getProviderPlanId(),
                        subscriptionId.toString(),
                        requester.getEmail())
                    .getUrl());
            } catch (StripeException e) {
                throw new RuntimeException("failed to create stripe checkout session", e);
            }
        }

        return result;
    }

    /**
     * <p>
     * Reconciles the internal state of the application by querying the changed subscription entity
     * from the Google API.</p>
     *
     * @param payload event payload.
     * @throws SubscriptionWebhookEventException on failing to correctly process the event payload.
     * @see <a href="https://developer.android.com/google/play/billing/rtdn-reference#sub">Google
     * Play real-time developer notifications reference</a>
     * @see <a href="https://developer.android.com/google/play/billing/subscriptions">Google Play
     * subscription documentation</a>
     * @see <a href="https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions">
     * Android Publisher REST API reference</a>
     */
    @ReasonablyTransactional
    public void handleGooglePlayWebhookEvent(@NonNull JsonNode payload) throws SubscriptionWebhookEventException {
        val data = payload.at("/message/data");
        if (!data.isTextual()) {
            throw new SubscriptionWebhookEventException("'message.data' field is missing or invalid in the payload");
        }

        final JsonNode event;
        try {
            event = objectMapper.readTree(Base64.decode(data.asText()));
        } catch (IOException e) {
            throw new SubscriptionWebhookEventException("failed to parse 'message.data' as json", e);
        }

        val subscriptionNotification = event.at("/subscriptionNotification");
        if (!subscriptionNotification.isObject()) {
            throw new SubscriptionWebhookEventException("'subscriptionNotification' object is missing or invalid in event payload");
        }

        val subscriptionPlanId = subscriptionNotification.at("/subscriptionId");
        if (!subscriptionPlanId.isTextual()) {
            throw new SubscriptionWebhookEventException("'subscriptionId' is missing or invalid in 'subscriptionNotification'");
        }

        val purchaseToken = subscriptionNotification.at("/purchaseToken");
        if (!purchaseToken.isTextual()) {
            throw new SubscriptionWebhookEventException("'purchaseToken' is missing or invalid in 'subscriptionNotification'");
        }

        final SubscriptionPurchase purchase;
        try {
            purchase = androidPublisherApi.getSubscriptionPurchase(
                subscriptionConfig.getAndroidApplicationId(),
                subscriptionPlanId.asText(),
                purchaseToken.asText());
        } catch (IOException e) {
            // might be a network error, no way to be sure.
            throw new RuntimeException("failed to retrieve purchase data from google play", e);
        }

        final long subscriptionId;
        try {
            subscriptionId = parseLong(purchase.getObfuscatedExternalProfileId());
        } catch (NumberFormatException e) {
            throw new SubscriptionWebhookEventException("failed to parse 'obfuscatedExternalProfileId' in subscription purchase", e);
        }

        val subscription = subscriptionRepository.findActiveById(subscriptionId)
            .orElseThrow(() -> {
                val errMsg = String.format("subscription with id '%d' doesn't exist", subscriptionId);
                return new SubscriptionWebhookEventException(errMsg);
            });

        // check if subscription has already been linked to a purchase
        if (subscription.getProviderSubscriptionId() != null) {
            throw new SubscriptionWebhookEventException("subscription is already linked to a purchase");
        }

        // https://developer.android.com/google/play/billing/subscriptions#lifecycle
        // tldr; if the expiry time is in the future, the user must have the subscription
        // entitlements. Their payment might be pending and in that case, if the expiry is in the
        // future, the user is in a grace-period (retains their entitlements). If the expiry is in
        // the past, the user's account is on hold, and they lose their entitlements. When the user
        // is in their grace-period, they should be notified about the pending payment.
        subscription.setProviderSubscriptionId(purchaseToken.asText());
        subscription.setStartAt(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(purchase.getStartTimeMillis()), ZoneId.systemDefault()));

        subscription.setEndAt(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(purchase.getExpiryTimeMillis()), ZoneId.systemDefault()));

        subscription.setStatus(Subscription.Status.INACTIVE);
        if (subscription.getEndAt().isAfter(LocalDateTime.now())) {
            subscription.setStatus(
                Integer.valueOf(0).equals(purchase.getPaymentState())
                    ? Subscription.Status.PENDING
                    : Subscription.Status.ACTIVE);
        }

        subscriptionRepository.save(subscription);

        // invalidate old (linked) subscription.
        // https://developer.android.com/google/play/billing/subscriptions#upgrade-downgrade
        subscriptionRepository.findActiveByProviderSubscriptionId(purchase.getLinkedPurchaseToken())
            .ifPresent(linkedSubscription -> {
                linkedSubscription.setStatus(Subscription.Status.INACTIVE);
                linkedSubscription.setEndAt(LocalDateTime.now());
                subscriptionRepository.save(linkedSubscription);
            });

        if (!Integer.valueOf(1).equals(purchase.getAcknowledgementState())) {
            try {
                androidPublisherApi.acknowledgePurchase(
                    subscriptionConfig.getAndroidApplicationId(),
                    subscriptionPlanId.asText(),
                    purchaseToken.asText());
            } catch (IOException e) {
                // might be a network error, no way to be sure.
                throw new RuntimeException("failed to acknowledge subscription purchase", e);
            }
        }
    }

    /**
     * @return a localised string after converting {@code paise} to INR
     */
    @NonNull
    private static String formatIndianPaiseToRupee(long paise) {
        val formatter = NumberFormat.getCurrencyInstance();
        formatter.setCurrency(Currency.getInstance("INR"));
        formatter.setMinimumFractionDigits(0);
        return formatter.format(paise / 100.0);
    }
}
