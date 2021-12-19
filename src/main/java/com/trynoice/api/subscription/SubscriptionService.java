package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.AccountService;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.platform.transaction.annotations.ReasonablyTransactional;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionStateException;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.subscription.models.SubscriptionView;
import lombok.NonNull;
import lombok.val;
import org.postgresql.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
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
                p = SubscriptionPlan.Provider.valueOf(provider.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedSubscriptionPlanProviderException(e);
            }

            plans = subscriptionPlanRepository.findAllActiveByProvider(p);
        } else {
            plans = subscriptionPlanRepository.findAllActive();
        }

        return plans.stream()
            .map(SubscriptionService::buildSubscriptionPlanView)
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
     * @param owner  user that initiated the subscription flow.
     * @param params subscription flow parameters.
     * @return a non-null {@link SubscriptionFlowResult}.
     * @throws SubscriptionPlanNotFoundException if the specified plan doesn't exist.
     * @throws DuplicateSubscriptionException    if the user already has an active/pending subscription.
     */
    @NonNull
    @ReasonablyTransactional
    public SubscriptionFlowResult createSubscription(
        @NonNull AuthUser owner,
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

        // At any given time, at most one subscription entity can exist per user with a non-inactive
        // status. If user already owns a subscription, an entity must be in active or pending
        // status. Otherwise, if the user has attempted to start a subscription previously, they
        // must own an entity with created status.
        val subscription = subscriptionRepository.findActiveByOwnerAndStatus(
            owner,
            Subscription.Status.CREATED,
            Subscription.Status.ACTIVE,
            Subscription.Status.PENDING);

        if (subscription.isPresent() && subscription.get().getStatus() != Subscription.Status.CREATED) {
            throw new DuplicateSubscriptionException();
        }

        val subscriptionId = subscription.orElseGet(
                () -> subscriptionRepository.save(
                    Subscription.builder()
                        .owner(owner)
                        .plan(plan)
                        .build()))
            .getId();

        val result = new SubscriptionFlowResult();
        result.setSubscriptionId(subscriptionId);
        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            try {
                result.setStripeCheckoutSessionUrl(
                    stripeApi.createCheckoutSession(
                            params.getSuccessUrl(),
                            params.getCancelUrl(),
                            plan.getProviderPlanId(),
                            subscriptionId.toString(),
                            owner.getEmail(),
                            subscriptionRepository.findActiveStripeCustomerIdByOwner(owner).orElse(null))
                        .getUrl());
            } catch (StripeException e) {
                throw new RuntimeException("failed to create stripe checkout session", e);
            }
        }

        return result;
    }

    /**
     * @param onlyActive      return on the active subscription (if any).
     * @param stripeReturnUrl optional redirect URL on exiting Stripe Customer portal (only used
     *                        when an active subscription exists and is provided by Stripe).
     * @return a list of active and inactive subscriptions owned by the given {@code owner}.
     */
    @NonNull
    List<SubscriptionView> getSubscriptions(@NonNull AuthUser owner, @NonNull Boolean onlyActive, String stripeReturnUrl) {
        final List<Subscription> subscriptions;
        if (onlyActive) {
            subscriptions = subscriptionRepository.findAllActiveByOwnerAndStatus(
                owner,
                Subscription.Status.ACTIVE,
                Subscription.Status.PENDING);
        } else {
            subscriptions = subscriptionRepository.findAllActiveByOwnerAndStatus(
                owner,
                Subscription.Status.ACTIVE,
                Subscription.Status.PENDING,
                Subscription.Status.INACTIVE);
        }

        // not listing subscriptions in created state.
        return subscriptions.stream()
            .map(subscription -> buildSubscriptionView(subscription, stripeApi, stripeReturnUrl))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Cancels the given subscription by marking it as inactive in app's internal state and
     * requesting its cancellation from its provider.
     *
     * @param owner          expected owner of the subscription (authenticated user).
     * @param subscriptionId id of the subscription to be cancelled.
     * @throws SubscriptionNotFoundException if subscription with given id and owner doesn't exist.
     * @throws SubscriptionStateException    if subscription is not currently active.
     */
    @ReasonablyTransactional
    public void cancelSubscription(
        @NonNull AuthUser owner,
        @NonNull Long subscriptionId
    ) throws SubscriptionNotFoundException, SubscriptionStateException {
        val subscription = subscriptionRepository.findActiveById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException("subscription doesn't exist"));

        if (!subscription.getOwner().equals(owner)) {
            throw new SubscriptionNotFoundException("given owner doesn't own this subscription");
        }

        if (subscription.getStatus() != Subscription.Status.ACTIVE && subscription.getStatus() != Subscription.Status.PENDING) {
            throw new SubscriptionStateException("subscription status is neither active nor pending");
        }

        switch (subscription.getPlan().getProvider()) {
            case GOOGLE_PLAY:
                try {
                    androidPublisherApi.cancelSubscription(
                        subscriptionConfig.getAndroidApplicationId(),
                        subscription.getPlan().getProviderPlanId(),
                        subscription.getProviderSubscriptionId());
                } catch (IOException e) {
                    throw new RuntimeException("google play api error", e);
                }

                break;
            case STRIPE:
                try {
                    stripeApi.cancelSubscription(subscription.getProviderSubscriptionId());
                } catch (StripeException e) {
                    throw new RuntimeException("stripe api error", e);
                }

                break;
            default:
                throw new IllegalStateException("unsupported provider used in subscription plan");
        }

        subscription.setStatus(Subscription.Status.INACTIVE);
        subscription.setEndAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    /**
     * <p>
     * Reconciles the internal state of the subscription entities by querying the changed
     * subscription purchase from the Google API.</p>
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
            // runtime exception so that webhook is retried since it might be a network error.
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
            throw new SubscriptionWebhookEventException("referenced subscription is already linked to a purchase");
        }

        // https://developer.android.com/google/play/billing/subscriptions#lifecycle
        // tldr; if the expiry time is in the future, the user must have the subscription
        // entitlements. Their payment might be pending and in that case, if the expiry is in the
        // future, the user is in a grace-period (retains their entitlements). If the expiry is in
        // the past, the user's account is on hold, and they lose their entitlements. When the user
        // is in their grace-period, they should be notified about the pending payment.
        subscription.setProviderSubscriptionId(purchaseToken.asText());
        if (purchase.getStartTimeMillis() != null) {
            subscription.setStartAtMillis(purchase.getStartTimeMillis());
        }

        if (purchase.getExpiryTimeMillis() != null) {
            subscription.setEndAtMillis(purchase.getExpiryTimeMillis());
        }

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
                // runtime exception so that webhook is retried since it might be a network error.
                throw new RuntimeException("failed to acknowledge subscription purchase", e);
            }
        }
    }

    /**
     * Reconciles the internal state of the subscription entities by processing the event payload.
     *
     * @param payload   event payload.
     * @param signature payload signature.
     * @throws SignatureVerificationException    on payload signature mismatch.
     * @throws SubscriptionWebhookEventException on failing to correctly process the event payload.
     * @see <a href="https://stripe.com/docs/billing/subscriptions/overview">How subscriptions work</a>
     * @see <a href="https://stripe.com/docs/billing/subscriptions/build-subscription?ui=checkout#provision-and-monitor">
     * Provision and monitor subscriptions</a>
     * @see <a href="https://stripe.com/docs/billing/subscriptions/webhooks">Subscription webhooks</a>
     * @see <a href="https://stripe.com/docs/api/checkout/sessions/object">Checkout Session object</a>
     * @see <a href="https://stripe.com/docs/api/subscriptions/object">Subscription object</a>
     */
    @ReasonablyTransactional
    public void handleStripeWebhookEvent(
        @NonNull String payload,
        @NonNull String signature
    ) throws SignatureVerificationException, SubscriptionWebhookEventException {
        val event = stripeApi.decodeWebhookPayload(payload, signature, subscriptionConfig.getStripeWebhookSecret());
        switch (event.getType()) {
            case "checkout.session.completed":
                val session = (Session) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new SubscriptionWebhookEventException("failed to get session object from the event payload"));

                if (session.getSubscription() == null) {
                    throw new SubscriptionWebhookEventException("checkout session subscription id is null");
                }

                try {
                    handleStripeCheckoutSessionEvent(session);
                } catch (SubscriptionWebhookEventException e) {
                    try {
                        // cancel subscription if we cannot handle the checkout session since
                        // SubscriptionWebhookEventException only occurs if Session is not how we
                        // expect it to be.
                        stripeApi.cancelSubscription(session.getSubscription());
                    } catch (StripeException inner) {
                        // runtime exception so that webhook is retried since it might be a network error.
                        throw new RuntimeException("failed to cancel stripe subscription", inner);
                    }

                    throw e;
                }

                break;
            case "customer.subscription.updated":
            case "customer.subscription.deleted":
            case "customer.subscription.pending_update_applied":
            case "customer.subscription.pending_update_expired":
            case "customer.subscription.trial_will_end":
                val stripeSubscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new SubscriptionWebhookEventException("failed to get subscription object from the event payload"));

                val subscription = subscriptionRepository.findActiveByProviderSubscriptionId(stripeSubscription.getId())
                    .orElseThrow(() -> {
                        val errMsg = String.format("subscription with providerSubscriptionId '%s' doesn't exist", stripeSubscription.getId());
                        return new SubscriptionWebhookEventException(errMsg);
                    });

                if (stripeSubscription.getStartDate() != null) {
                    subscription.setStartAtSeconds(stripeSubscription.getStartDate());
                } else if (stripeSubscription.getCurrentPeriodStart() != null) {
                    subscription.setStartAtSeconds(stripeSubscription.getCurrentPeriodStart());
                }

                if (stripeSubscription.getEndedAt() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getEndedAt());
                } else if (stripeSubscription.getCurrentPeriodEnd() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getCurrentPeriodEnd());
                }

                switch (stripeSubscription.getStatus()) {
                    case "trialing":
                    case "active":
                        subscription.setStatus(Subscription.Status.ACTIVE);
                        break;
                    case "past_due":
                        subscription.setStatus(Subscription.Status.PENDING);
                        break;
                    default:
                        subscription.setStatus(Subscription.Status.INACTIVE);
                        break;
                }

                subscriptionRepository.save(subscription);
                break;
        }
    }

    private void handleStripeCheckoutSessionEvent(@NonNull Session session) throws SubscriptionWebhookEventException {
        if (!"subscription".equals(session.getMode())) {
            throw new SubscriptionWebhookEventException("checkout session mode is not subscription");
        } else if (!"complete".equals(session.getStatus())) {
            throw new SubscriptionWebhookEventException("checkout session status is not complete");
        } else if (!"paid".equals(session.getPaymentStatus())) {
            throw new SubscriptionWebhookEventException("checkout session payment status is not paid");
        } else if (session.getCustomer() == null) {
            throw new SubscriptionWebhookEventException("customer id is missing from the checkout session");
        }

        try {
            val subscriptionId = parseLong(session.getClientReferenceId());
            val subscription = subscriptionRepository.findActiveById(subscriptionId)
                .orElseThrow(() -> {
                    val errMsg = String.format("subscription with id '%d' doesn't exist", subscriptionId);
                    return new SubscriptionWebhookEventException(errMsg);
                });

            subscription.setProviderSubscriptionId(session.getSubscription());
            subscription.setStripeCustomerId(session.getCustomer());
            subscription.setStatus(Subscription.Status.ACTIVE);
            subscription.setStartAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
        } catch (NumberFormatException e) {
            throw new SubscriptionWebhookEventException("failed to parse the client reference id in checkout session");
        }
    }

    @NonNull
    private static SubscriptionPlanView buildSubscriptionPlanView(@NonNull SubscriptionPlan plan) {
        return SubscriptionPlanView.builder()
            .id(plan.getId())
            .provider(plan.getProvider().name().toLowerCase())
            .billingPeriodMonths(plan.getBillingPeriodMonths())
            .priceInr(formatIndianPaiseToRupee(plan.getPriceInIndianPaise()))
            .build();
    }

    @NonNull
    private static SubscriptionView buildSubscriptionView(
        @NonNull Subscription subscription,
        @NonNull StripeApi stripeApi,
        String stripeReturnUrl
    ) {
        val isPaymentPending = subscription.getStatus() == Subscription.Status.PENDING;
        val isActive = isPaymentPending || subscription.getStatus() == Subscription.Status.ACTIVE;
        val endedAt = subscription.getEndAt() != null && subscription.getEndAt().isBefore(LocalDateTime.now())
            ? subscription.getEndAt() : null;

        final String stripeCustomerPortalUrl;
        if (isActive && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE) {
            try {
                stripeCustomerPortalUrl = stripeApi.createCustomerPortalSession(
                        subscription.getStripeCustomerId(),
                        stripeReturnUrl)
                    .getUrl();
            } catch (StripeException e) {
                throw new RuntimeException("stripe api error", e);
            }
        } else {
            stripeCustomerPortalUrl = null;
        }

        return SubscriptionView.builder()
            .id(subscription.getId())
            .plan(buildSubscriptionPlanView(subscription.getPlan()))
            .isActive(isActive)
            .isPaymentPending(isPaymentPending)
            .startedAt(subscription.getStartAt())
            .endedAt(endedAt)
            .stripeCustomerPortalUrl(stripeCustomerPortalUrl)
            .build();
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
