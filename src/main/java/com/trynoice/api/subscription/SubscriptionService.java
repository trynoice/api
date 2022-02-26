package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.contracts.SoundSubscriptionServiceContract;
import com.trynoice.api.contracts.SubscriptionAccountServiceContract;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.subscription.models.SubscriptionView;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;

/**
 * {@link SubscriptionService} implements operations related to subscription management.
 */
@Service
class SubscriptionService implements SoundSubscriptionServiceContract {

    private final SubscriptionConfiguration subscriptionConfig;
    private final CustomerRepository customerRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionAccountServiceContract accountServiceContract;
    private final AndroidPublisherApi androidPublisherApi;
    private final StripeApi stripeApi;

    @Autowired
    SubscriptionService(
        @NonNull SubscriptionConfiguration subscriptionConfig,
        @NonNull CustomerRepository customerRepository,
        @NonNull SubscriptionPlanRepository subscriptionPlanRepository,
        @NonNull SubscriptionRepository subscriptionRepository,
        @NonNull ObjectMapper objectMapper,
        @NonNull SubscriptionAccountServiceContract accountServiceContract,
        @NonNull AndroidPublisherApi androidPublisherApi,
        @NonNull StripeApi stripeApi
    ) {
        this.subscriptionConfig = subscriptionConfig;
        this.customerRepository = customerRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.accountServiceContract = accountServiceContract;
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
        final Iterable<SubscriptionPlan> plans;
        if (provider != null) {
            final SubscriptionPlan.Provider p;
            try {
                p = SubscriptionPlan.Provider.valueOf(provider.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedSubscriptionPlanProviderException(e);
            }

            plans = subscriptionPlanRepository.findAllByProvider(p);
        } else {
            plans = subscriptionPlanRepository.findAll();
        }

        return StreamSupport.stream(plans.spliterator(), false)
            .map(SubscriptionService::buildSubscriptionPlanView)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * <p>
     * Initiates the subscription flow by creating a new {@code incomplete} subscription entity.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#STRIPE}, it also
     * returns a non-null {@link SubscriptionFlowResult#getStripeCheckoutSessionUrl() checkout
     * session url}. The clients must redirect user to the checkout session url to conclude the
     * subscription flow.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#GOOGLE_PLAY}, the
     * clients must link the returned {@link SubscriptionFlowResult#getSubscriptionId() subscription
     * id} to the Google Play subscription purchase by specifying it as {@code obfuscatedProfileId}
     * in Google Play billing flow parameters.</p>
     *
     * @param customerId id of the customer (user) that initiated the subscription flow.
     * @param params     subscription flow parameters.
     * @return a non-null {@link SubscriptionFlowResult}.
     * @throws SubscriptionPlanNotFoundException if the specified plan doesn't exist.
     * @throws DuplicateSubscriptionException    if the user already has an active/pending subscription.
     */
    @NonNull
    @Transactional(rollbackFor = Throwable.class)
    public SubscriptionFlowResult createSubscription(
        @NonNull Long customerId,
        @NonNull SubscriptionFlowParams params
    ) throws SubscriptionPlanNotFoundException, DuplicateSubscriptionException {
        val plan = subscriptionPlanRepository.findById(params.getPlanId())
            .orElseThrow(SubscriptionPlanNotFoundException::new);

        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            if (params.getSuccessUrl() == null) {
                throw new ConstraintViolationException("'successUrl' is required for 'STRIPE' plans", null);
            }

            if (params.getCancelUrl() == null) {
                throw new ConstraintViolationException("'cancelUrl' is required for 'STRIPE' plans", null);
            }
        }

        if (subscriptionRepository.existsActiveByCustomerUserId(customerId)) {
            throw new DuplicateSubscriptionException();
        }

        val customer = customerRepository.findById(customerId)
            .orElseGet(() -> customerRepository.save(
                Customer.builder()
                    .userId(customerId)
                    .build()));

        val subscription = subscriptionRepository.save(
            Subscription.builder()
                .customer(customer)
                .plan(plan)
                .build());

        val result = new SubscriptionFlowResult();
        result.setSubscriptionId(subscription.getId());
        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            try {
                result.setStripeCheckoutSessionUrl(
                    stripeApi.createCheckoutSession(
                            params.getSuccessUrl(),
                            params.getCancelUrl(),
                            plan.getProviderPlanId(),
                            String.valueOf(subscription.getId()),
                            accountServiceContract.findEmailByUser(customerId).orElse(null),
                            customer.getStripeId(),
                            (long) plan.getTrialPeriodDays())
                        .getUrl());
            } catch (StripeException e) {
                throw new RuntimeException("failed to create stripe checkout session", e);
            }
        }

        return result;
    }

    /**
     * Lists all subscriptions purchased by a customer with given {@code customerId}.  If {@code
     * onlyActive} is {@literal true}, it lists the currently active subscription purchase (at most
     * one).
     *
     * @param customerId      id of the customer (user) that purchased the subscriptions.
     * @param onlyActive      return only the active subscription (if any).
     * @param stripeReturnUrl optional redirect URL on exiting Stripe Customer portal (only used
     *                        when an active subscription exists and is provided by Stripe).
     * @return a list of subscription purchased by the given {@code customerId}.
     */
    @NonNull
    List<SubscriptionView> listSubscriptions(@NonNull Long customerId, @NonNull Boolean onlyActive, String stripeReturnUrl) {
        final List<Subscription> subscriptions;
        if (onlyActive) {
            subscriptions = subscriptionRepository.findActiveByCustomerUserId(customerId)
                .stream()
                .collect(Collectors.toUnmodifiableList());
        } else {
            subscriptions = subscriptionRepository.findAllByCustomerUserId(customerId);
        }

        val stripeCustomerPortalUrl = subscriptions.stream()
            .anyMatch(s -> s.isActive() && s.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE)
            ? createStripeCustomerSession(subscriptions.get(0).getCustomer(), stripeReturnUrl)
            : null;

        return subscriptions.stream()
            .map(subscription -> buildSubscriptionView(subscription, stripeCustomerPortalUrl))
            .collect(Collectors.toUnmodifiableList());
    }

    private String createStripeCustomerSession(@NonNull Customer customer, String returnUrl) {
        if (customer.getStripeId() == null) {
            return null;
        }

        try {
            return stripeApi.createCustomerPortalSession(customer.getStripeId(), returnUrl).getUrl();
        } catch (StripeException e) {
            throw new RuntimeException("stripe api error", e);
        }
    }

    /**
     * Cancels the given subscription by requesting its cancellation from its provider and marking
     * it as inactive in our internal state.
     *
     * @param customerId     id of the customer (user) that purchased the subscription.
     * @param subscriptionId id of the subscription to be cancelled.
     * @throws SubscriptionNotFoundException if an active subscription with given id and owner doesn't exist.
     */
    @Transactional(rollbackFor = Throwable.class)
    public void cancelSubscription(@NonNull Long customerId, @NonNull Long subscriptionId) throws SubscriptionNotFoundException {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException("subscription doesn't exist"));

        if (!customerId.equals(subscription.getCustomer().getUserId())) {
            throw new SubscriptionNotFoundException("given owner doesn't own this subscription");
        }

        if (!subscription.isActive()) {
            throw new SubscriptionNotFoundException("given subscription is not currently active");
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

        subscription.setPaymentPending(false);
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
    @Transactional(rollbackFor = Throwable.class)
    public void handleGooglePlayWebhookEvent(@NonNull JsonNode payload) throws SubscriptionWebhookEventException {
        val data = payload.at("/message/data");
        if (!data.isTextual()) {
            throw new SubscriptionWebhookEventException("'message.data' field is missing or invalid in the payload");
        }

        final JsonNode event;
        try {
            event = objectMapper.readTree(Base64.getDecoder().decode(data.asText()));
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

        val subscription = subscriptionRepository.findById(subscriptionId)
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

        if (subscription.isActive()) {
            subscription.setPaymentPending(Integer.valueOf(0).equals(purchase.getPaymentState()));
        }

        subscriptionRepository.save(subscription);

        // invalidate old (linked) subscription.
        // https://developer.android.com/google/play/billing/subscriptions#upgrade-downgrade
        subscriptionRepository.findByProviderSubscriptionId(purchase.getLinkedPurchaseToken())
            .ifPresent(linkedSubscription -> {
                linkedSubscription.setPaymentPending(false);
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
    @Transactional(rollbackFor = Throwable.class)
    public void handleStripeWebhookEvent(
        @NonNull String payload,
        @NonNull String signature
    ) throws SignatureVerificationException, SubscriptionWebhookEventException {
        val event = stripeApi.decodeWebhookPayload(payload, signature, subscriptionConfig.getStripeWebhookSecret());
        switch (event.getType()) {
            case "checkout.session.completed":
                val session = (Session) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new SubscriptionWebhookEventException("failed to get session object from the event payload"));

                try {
                    handleStripeCheckoutSessionEvent(session);
                } catch (SubscriptionWebhookEventException outer) {
                    try {
                        // cancel subscription if we cannot handle the checkout session since
                        // SubscriptionWebhookEventException only occurs if Session is not how we
                        // expect it to be.
                        stripeApi.cancelSubscription(session.getSubscription());
                    } catch (StripeException inner) {
                        // runtime exception so that webhook is retried since it might be a network error.
                        throw new RuntimeException("failed to cancel stripe subscription", inner);
                    }

                    throw outer;
                }
                break;
            case "customer.subscription.updated":
            case "customer.subscription.deleted":
            case "customer.subscription.pending_update_applied":
            case "customer.subscription.pending_update_expired":
            case "customer.subscription.trial_will_end":
                val stripeSubscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new SubscriptionWebhookEventException("failed to get subscription object from the event payload"));
                handleStripeSubscriptionEvent(stripeSubscription);
                break;
        }
    }

    private void handleStripeCheckoutSessionEvent(@NonNull Session session) throws SubscriptionWebhookEventException {
        if (!"subscription".equals(session.getMode())) {
            throw new SubscriptionWebhookEventException("checkout session mode is not subscription");
        } else if (session.getSubscription() == null) {
            throw new SubscriptionWebhookEventException("checkout session subscription id is null");
        } else if (!"complete".equals(session.getStatus())) {
            throw new SubscriptionWebhookEventException("checkout session status is not complete");
        } else if (!"paid".equals(session.getPaymentStatus())) {
            throw new SubscriptionWebhookEventException("checkout session payment status is not paid");
        } else if (session.getCustomer() == null) {
            throw new SubscriptionWebhookEventException("customer id is missing from the checkout session");
        }

        final long subscriptionId;
        try {
            subscriptionId = parseLong(session.getClientReferenceId());
        } catch (NumberFormatException e) {
            throw new SubscriptionWebhookEventException("failed to parse the client reference id in checkout session");
        }

        final com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeApi.getSubscription(session.getSubscription());
        } catch (StripeException e) {
            throw new SubscriptionWebhookEventException("failed to get subscription object from stripe api", e);
        }

        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> {
                val errMsg = String.format("subscription with id '%d' doesn't exist", subscriptionId);
                return new SubscriptionWebhookEventException(errMsg);
            });

        subscription.setProviderSubscriptionId(session.getSubscription());
        updateSubscriptionDetailsFromStripeObject(subscription, stripeSubscription);
        subscriptionRepository.save(subscription);

        val customer = subscription.getCustomer();
        if (customer.getStripeId() == null || !customer.getStripeId().equals(session.getCustomer())) {
            customer.setStripeId(session.getCustomer());
            customerRepository.save(customer);
        }
    }

    private void handleStripeSubscriptionEvent(
        @NonNull com.stripe.model.Subscription stripeSubscription
    ) throws SubscriptionWebhookEventException {
        val subscription = subscriptionRepository.findByProviderSubscriptionId(stripeSubscription.getId())
            .orElseThrow(() -> {
                val errMsg = String.format("subscription with providerSubscriptionId '%s' doesn't exist", stripeSubscription.getId());
                return new SubscriptionWebhookEventException(errMsg);
            });

        updateSubscriptionDetailsFromStripeObject(subscription, stripeSubscription);
        subscriptionRepository.save(subscription);
    }

    private void updateSubscriptionDetailsFromStripeObject(
        @NonNull Subscription subscription,
        @NonNull com.stripe.model.Subscription stripeSubscription
    ) throws SubscriptionWebhookEventException {
        if (stripeSubscription.getItems() == null
            || stripeSubscription.getItems().getData() == null
            || stripeSubscription.getItems().getData().size() != 1
            || stripeSubscription.getItems().getData().get(0) == null
            || stripeSubscription.getItems().getData().get(0).getPrice() == null) {
            throw new SubscriptionWebhookEventException("failed to get subscription price object from the event payload");
        }

        val stripePrice = stripeSubscription.getItems().getData().get(0).getPrice();
        if (stripePrice.getId() != null && !subscription.getPlan().getProviderPlanId().equals(stripePrice.getId())) {
            subscription.setPlan(
                subscriptionPlanRepository.findByProviderPlanId(SubscriptionPlan.Provider.STRIPE, stripePrice.getId())
                    .orElseThrow(() -> new SubscriptionWebhookEventException("updated provider plan id not recognised")));
        }

        if (stripeSubscription.getStartDate() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getStartDate());
        } else if (stripeSubscription.getCurrentPeriodStart() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getCurrentPeriodStart());
        }

        subscription.setPaymentPending("past_due".equals(stripeSubscription.getStatus()));
        switch (stripeSubscription.getStatus()) {
            case "trialing":
            case "active":
            case "past_due":
                if (stripeSubscription.getEndedAt() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getEndedAt());
                } else if (stripeSubscription.getCurrentPeriodEnd() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getCurrentPeriodEnd());
                }
                break;
            default:
                subscription.setEndAt(LocalDateTime.now());
                break;
        }

        subscriptionRepository.save(subscription);
    }

    /**
     * @return if the user with given {@code userId} owns an active subscription.
     */
    @Override
    public boolean isUserSubscribed(@NonNull Long userId) {
        return subscriptionRepository.existsActiveByCustomerUserId(userId);
    }

    @NonNull
    private static SubscriptionPlanView buildSubscriptionPlanView(@NonNull SubscriptionPlan plan) {
        return SubscriptionPlanView.builder()
            .id(plan.getId())
            .provider(plan.getProvider().name().toLowerCase())
            .billingPeriodMonths(plan.getBillingPeriodMonths())
            .trialPeriodDays(plan.getTrialPeriodDays())
            .priceInIndianPaise(plan.getPriceInIndianPaise())
            .googlePlaySubscriptionId(
                plan.getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                    ? plan.getProviderPlanId()
                    : null)
            .build();
    }

    @NonNull
    private static SubscriptionView buildSubscriptionView(@NonNull Subscription subscription, String stripeCustomerPortalUrl) {
        return SubscriptionView.builder()
            .id(subscription.getId())
            .plan(buildSubscriptionPlanView(subscription.getPlan()))
            .status(
                subscription.isActive() ? subscription.isPaymentPending()
                    ? SubscriptionView.STATUS_PENDING
                    : SubscriptionView.STATUS_ACTIVE
                    : SubscriptionView.STATUS_INACTIVE)
            .startedAt(subscription.getStartAt())
            .endedAt(
                subscription.getEndAt() != null && subscription.getEndAt().isBefore(LocalDateTime.now())
                    ? subscription.getEndAt() : null)
            .stripeCustomerPortalUrl(
                subscription.isActive() && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE
                    ? stripeCustomerPortalUrl : null)
            .build();
    }
}
