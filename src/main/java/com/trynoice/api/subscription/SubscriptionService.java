package com.trynoice.api.subscription;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.contracts.SubscriptionServiceContract;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.GiftCard;
import com.trynoice.api.subscription.entities.GiftCardRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionPlanRepository;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.GiftCardExpiredException;
import com.trynoice.api.subscription.exceptions.GiftCardNotFoundException;
import com.trynoice.api.subscription.exceptions.GiftCardRedeemedException;
import com.trynoice.api.subscription.exceptions.StripeCustomerPortalUrlException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.exceptions.WebhookEventException;
import com.trynoice.api.subscription.exceptions.WebhookPayloadException;
import com.trynoice.api.subscription.payload.GiftCardResponse;
import com.trynoice.api.subscription.payload.GooglePlayDeveloperNotification;
import com.trynoice.api.subscription.payload.StripeCustomerPortalUrlResponse;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionFlowResponseV2;
import com.trynoice.api.subscription.payload.SubscriptionPlanResponse;
import com.trynoice.api.subscription.payload.SubscriptionResponseV2;
import com.trynoice.api.subscription.upstream.AndroidPublisherApi;
import com.trynoice.api.subscription.upstream.ForeignExchangeRatesProvider;
import com.trynoice.api.subscription.upstream.StripeApi;
import com.trynoice.api.subscription.upstream.models.GooglePlaySubscriptionPurchase;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNullElse;

/**
 * {@link SubscriptionService} implements operations related to subscription management.
 */
@Service
class SubscriptionService implements SubscriptionServiceContract {

    private final SubscriptionConfiguration subscriptionConfig;
    private final CustomerRepository customerRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GiftCardRepository giftCardRepository;
    private final AccountServiceContract accountServiceContract;
    private final AndroidPublisherApi androidPublisherApi;
    private final StripeApi stripeApi;
    private final Cache cache;
    private final ForeignExchangeRatesProvider exchangeRatesProvider;

    @Autowired
    SubscriptionService(
        @NonNull SubscriptionConfiguration subscriptionConfig,
        @NonNull CustomerRepository customerRepository,
        @NonNull SubscriptionPlanRepository subscriptionPlanRepository,
        @NonNull SubscriptionRepository subscriptionRepository,
        @NonNull GiftCardRepository giftCardRepository,
        @NonNull AccountServiceContract accountServiceContract,
        @NonNull AndroidPublisherApi androidPublisherApi,
        @NonNull StripeApi stripeApi,
        @NonNull @Qualifier(SubscriptionBeans.CACHE_NAME) Cache cache,
        @NonNull ForeignExchangeRatesProvider exchangeRatesProvider
    ) {
        this.subscriptionConfig = subscriptionConfig;
        this.customerRepository = customerRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.giftCardRepository = giftCardRepository;
        this.accountServiceContract = accountServiceContract;
        this.androidPublisherApi = androidPublisherApi;
        this.stripeApi = stripeApi;
        this.cache = cache;
        this.exchangeRatesProvider = exchangeRatesProvider;
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
     * @return a non-null list of {@link SubscriptionPlanResponse}.
     * @throws UnsupportedSubscriptionPlanProviderException if an invalid {@code provider} is given.
     */
    @NonNull
    List<SubscriptionPlanResponse> listPlans(String provider, String currencyCode) throws UnsupportedSubscriptionPlanProviderException {
        final Iterable<SubscriptionPlan> plans;
        val sortOrder = Sort.by(Sort.Order.asc("priceInIndianPaise"));
        if (provider != null) {
            final SubscriptionPlan.Provider p;
            try {
                p = SubscriptionPlan.Provider.valueOf(provider.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedSubscriptionPlanProviderException(e);
            }

            plans = subscriptionPlanRepository.findAllByProvider(p, sortOrder);
        } else {
            plans = subscriptionPlanRepository.findAll(sortOrder);
        }

        val exchangeRate = currencyCode == null ? null : exchangeRatesProvider.getRateForCurrency("INR", currencyCode).orElse(null);
        return StreamSupport.stream(plans.spliterator(), false)
            .filter(p -> p.getProvider() != SubscriptionPlan.Provider.GIFT_CARD) // do not return GIFT_CARD plan(s)
            .map(p -> buildSubscriptionPlanResponse(p, currencyCode, exchangeRate))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * <p>
     * Initiates the subscription flow by creating a new {@code incomplete} subscription entity and
     * returns it with the result.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#STRIPE}, it also
     * returns a non-null {@link SubscriptionFlowResponseV2#getStripeCheckoutSessionUrl() checkout
     * session url}. The clients must redirect user to the checkout session url to conclude the
     * subscription flow.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#GOOGLE_PLAY}, the
     * clients must link the returned {@code subscriptionId} to the Google Play subscription
     * purchase by specifying it as {@code obfuscatedAccountId} in Google Play billing flow
     * parameters.</p>
     *
     * @param customerId id of the customer (user) that initiated the subscription flow.
     * @param params     subscription flow parameters.
     * @return a non-null {@link SubscriptionFlowResponseV2}.
     * @throws SubscriptionPlanNotFoundException if the specified plan doesn't exist.
     * @throws DuplicateSubscriptionException    if the user already has an active/pending subscription.
     */
    @NonNull
    @Transactional(rollbackFor = Throwable.class)
    public SubscriptionFlowResponseV2 createSubscription(
        @NonNull Long customerId,
        @NonNull SubscriptionFlowParams params
    ) throws SubscriptionPlanNotFoundException, DuplicateSubscriptionException {
        val plan = subscriptionPlanRepository.findById(params.getPlanId())
            .orElseThrow(SubscriptionPlanNotFoundException::new);

        if (plan.getProvider() == SubscriptionPlan.Provider.STRIPE) {
            if (params.getSuccessUrl() == null) {
                throw new ConstraintViolationException("'successUrl' is required for 'stripe' plans", null);
            }

            if (params.getCancelUrl() == null) {
                throw new ConstraintViolationException("'cancelUrl' is required for 'stripe' plans", null);
            }
        }

        if (subscriptionRepository.existsActiveByCustomerUserId(customerId)) {
            throw new DuplicateSubscriptionException();
        }

        val customer = getOrCreateCustomer(customerId);
        val subscription = subscriptionRepository.save(
            Subscription.builder()
                .customer(customer)
                .plan(plan)
                .build());

        val result = new SubscriptionFlowResponseV2();
        result.setSubscription(buildSubscriptionResponse(subscription, null, null));
        result.setSubscriptionId(subscription.getId());
        if (plan.getProvider() != SubscriptionPlan.Provider.STRIPE) {
            return result;
        }

        try {
            val toReplaceRegex = "(\\{|%7B)subscriptionId(}|%7D)";
            val subscriptionIdStr = String.valueOf(subscription.getId());
            val checkoutSession = stripeApi.createCheckoutSession(
                params.getSuccessUrl().replaceAll(toReplaceRegex, subscriptionIdStr),
                params.getCancelUrl().replaceAll(toReplaceRegex, subscriptionIdStr),
                plan.getProvidedId(),
                subscriptionConfig.getStripeCheckoutSessionExpiry(),
                subscriptionIdStr,
                customer.getStripeId() == null
                    ? accountServiceContract.findEmailByUser(customerId).orElse(null)
                    : null,
                customer.getStripeId(),
                customer.isTrialPeriodUsed() ? null : (long) plan.getTrialPeriodDays());

            result.setStripeCheckoutSessionUrl(checkoutSession.getUrl());
        } catch (StripeException e) {
            throw new RuntimeException("failed to create stripe checkout session", e);
        }

        return result;
    }

    @NonNull
    private Customer getOrCreateCustomer(long customerId) {
        return customerRepository.findById(customerId)
            .orElseGet(() -> customerRepository.save(
                Customer.builder()
                    .userId(customerId)
                    .build()));
    }

    /**
     * Lists a page of subscriptions purchased by a customer with given {@code customerId}. Each
     * page contains at-most 20 entries. If the {@code pageNumber} is higher than the available
     * pages, it returns an empty list. If {@code onlyActive} is {@literal true}, it lists the
     * currently active subscription purchase (at most one).
     *
     * @param customerId id of the customer (user) that purchased the subscriptions.
     * @param onlyActive return only the active subscription (if any).
     * @param pageNumber a not {@literal null} 0-indexed page number.
     * @return a list of subscription purchased by the given {@code customerId}.
     */
    @NonNull
    List<SubscriptionResponseV2> listSubscriptions(
        @NonNull Long customerId,
        @NonNull Boolean onlyActive,
        String currencyCode,
        @NonNull Integer pageNumber
    ) {
        final List<Subscription> subscriptions;
        if (onlyActive) {
            subscriptions = subscriptionRepository.findActiveByCustomerUserId(customerId)
                .stream()
                .collect(Collectors.toUnmodifiableList());
        } else {
            val pageable = PageRequest.of(pageNumber, 20, Sort.by(Sort.Order.desc("startAt")));
            subscriptions = subscriptionRepository.findAllStartedByCustomerUserId(customerId, pageable).toList();
        }

        val exchangeRate = currencyCode == null ? null : exchangeRatesProvider.getRateForCurrency("INR", currencyCode).orElse(null);
        return subscriptions.stream()
            .map(subscription -> buildSubscriptionResponse(subscription, currencyCode, exchangeRate))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get a subscription purchased by a customer by its {@code subscriptionId}.
     *
     * @param customerId     must not be {@literal null}.
     * @param subscriptionId must not be {@literal null}.
     * @return the requested subscription, guaranteed to be not {@literal null}.
     * @throws SubscriptionNotFoundException if such a subscription doesn't exist.
     */
    @NonNull
    public SubscriptionResponseV2 getSubscription(
        @NonNull Long customerId,
        @NonNull Long subscriptionId,
        String currencyCode
    ) throws SubscriptionNotFoundException {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException("subscription with given id doesn't exist"));

        if (!customerId.equals(subscription.getCustomer().getUserId())) {
            throw new SubscriptionNotFoundException("subscription with given id is not owned by the customer");
        }

        if (subscription.getStartAt() == null) {
            throw new SubscriptionNotFoundException("subscription has not started yet");
        }

        return buildSubscriptionResponse(
            subscription,
            currencyCode,
            currencyCode == null
                ? null
                : exchangeRatesProvider.getRateForCurrency("INR", currencyCode).orElse(null));
    }

    /**
     * Cancels the given subscription by requesting its cancellation from its provider. All
     * providers are configured to cancel subscriptions at the end of their current billing cycles.
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
                    androidPublisherApi.cancelSubscription(subscription.getProvidedId());
                } catch (IOException e) {
                    throw new RuntimeException("google play api error", e);
                }

                break;
            case STRIPE:
                try {
                    // cancel at the end of current billing period to match Google Play Subscriptions behaviour.
                    stripeApi.cancelSubscription(subscription.getProvidedId());
                } catch (StripeException e) {
                    throw new RuntimeException("stripe api error", e);
                }

                break;
            case GIFT_CARD:
                // no-op as gift card subscriptions are not auto-renewing.
                break;
            default:
                throw new IllegalStateException("unsupported provider used in subscription plan");
        }

        subscription.setAutoRenewing(false);
        subscriptionRepository.save(subscription);
    }

    /**
     * <p>
     * Reconciles the internal state of the subscription entities by querying the changed
     * subscription purchase from the Google Play Developers API.</p>
     *
     * @param payload event payload.
     * @throws WebhookEventException on failing to correctly process the event.
     * @see <a href="https://developer.android.com/google/play/billing/rtdn-reference#sub">Google
     * Play real-time developer notifications reference</a>
     * @see <a href="https://developer.android.com/google/play/billing/subscriptions">Google Play
     * subscription documentation</a>
     * @see <a href="https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions">
     * Android Publisher REST API reference</a>
     */
    @ServiceActivator(inputChannel = SubscriptionBeans.GOOGLE_PLAY_DEVELOPER_NOTIFICATION_CHANNEL)
    @Transactional(rollbackFor = Throwable.class)
    public void handleGooglePlayDeveloperNotification(
        @NonNull GooglePlayDeveloperNotification payload
    ) throws WebhookEventException {
        val notification = payload.getSubscriptionNotification();
        if (notification == null) {
            // this is not a subscription notification if the object is missing. return normally.
            return;
        }

        final GooglePlaySubscriptionPurchase purchase;
        try {
            purchase = androidPublisherApi.getSubscriptionPurchase(notification.getPurchaseToken());
        } catch (IOException e) {
            throw new RuntimeException("failed to retrieve purchase data from google play", e);
        }

        if (purchase.isTestPurchase() != subscriptionConfig.isGooglePlayTestModeEnabled()) {
            // ignore notifications if we're not running in the same mode as the purchased item.
            return;
        }

        final long subscriptionId;
        try {
            subscriptionId = parseLong(purchase.getObfuscatedExternalAccountId());
        } catch (NumberFormatException e) {
            throw new WebhookEventException("failed to parse 'obfuscatedExternalAccountId' in subscription purchase", e);
        }

        // On upgrading/downgrading subscription, Google Play creates a new subscription entity and
        // delivers its notification before expiring the old subscription. Hence, when an
        // upgrade/downgrade happens, we need prevent the old subscription's notification from
        // mutating our internal state. To achieve that, we rely on querying internal subscription
        // objects using purchase tokens (provided id).
        val subscription = notification.getNotificationType() == GooglePlayDeveloperNotification.SubscriptionNotification.TYPE_PURCHASED
            ? subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new WebhookEventException("failed to find subscription entity for this purchase"))
            : subscriptionRepository.findByProvidedId(notification.getPurchaseToken())
            .orElseGet(() -> purchase.getLinkedPurchaseToken() != null
                ? subscriptionRepository.findByProvidedId(purchase.getLinkedPurchaseToken()).orElse(null)
                : null);

        if (subscription == null) {
            // subscription is missing; the purchase token may have been invalidated during an
            // upgrade/downgrade.
            return;
        }

        subscription.setProvidedId(notification.getPurchaseToken());
        if (hasAnotherActiveSubscription(subscription)) {
            subscription.setAutoRenewing(false);
            subscription.setPaymentPending(false);
            subscription.setRefunded(true);
            subscription.setStartAtMillis(requireNonNullElse(purchase.getStartTimeMillis(), System.currentTimeMillis()));
            if (subscription.getEndAt() == null || subscription.getEndAt().isAfter(OffsetDateTime.now())) {
                subscription.setEndAt(OffsetDateTime.now());
            }

            subscriptionRepository.save(subscription);
            return; // without acknowledging purchase so that Google Play refunds it.
        }

        if (purchase.getProductId() != null && !purchase.getProductId().equals(subscription.getPlan().getProvidedId())) {
            subscription.setPlan(
                subscriptionPlanRepository.findByProvidedId(
                        SubscriptionPlan.Provider.GOOGLE_PLAY, purchase.getProductId())
                    .orElseThrow(() -> new WebhookEventException("unknown provider plan id")));
        }

        // https://developer.android.com/google/play/billing/subscriptions#lifecycle
        // tldr; if the expiry time is in the future, the user must have the subscription
        // entitlements. Their payment might be pending and in that case, if the expiry is in the
        // future, the user is in a grace-period (retains their entitlements). If the expiry is in
        // the past, the user's account is on hold, and they lose their entitlements. When the user
        // is in their grace-period, they should be notified about the pending payment.
        if (purchase.getStartTimeMillis() != null) {
            subscription.setStartAtMillis(purchase.getStartTimeMillis());
        }

        if (purchase.getExpiryTimeMillis() != null) {
            subscription.setEndAtMillis(purchase.getExpiryTimeMillis());
        }

        if (subscription.isActive()) {
            subscription.setPaymentPending(purchase.isPaymentPending());
        } else {
            subscription.setPaymentPending(false);
        }

        subscription.setAutoRenewing(purchase.isAutoRenewing());
        subscriptionRepository.save(subscription);

        if (!subscription.getCustomer().isTrialPeriodUsed()) {
            subscription.getCustomer().setTrialPeriodUsed(true);
            customerRepository.save(subscription.getCustomer());
        }

        if (!purchase.isAcknowledged()) {
            try {
                androidPublisherApi.acknowledgePurchase(purchase.getProductId(), notification.getPurchaseToken());
            } catch (IOException e) {
                throw new RuntimeException("failed to acknowledge subscription purchase", e);
            }
        }

        evictIsSubscribedCache(subscription.getCustomer().getUserId());
    }

    /**
     * Reconciles the internal state of the subscription entities by processing the event payload.
     *
     * @param payload   event payload.
     * @param signature payload signature.
     * @throws WebhookPayloadException on failing to correctly parse the event payload.
     * @throws WebhookEventException   on failing to correctly process the event.
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
    ) throws WebhookPayloadException, WebhookEventException {
        final Event event;
        try {
            event = stripeApi.decodeWebhookPayload(payload, signature, subscriptionConfig.getStripeWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new WebhookPayloadException("failed to verify payload signature", e);
        }

        switch (event.getType()) {
            case "checkout.session.completed":
                val session = (Session) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new WebhookPayloadException("failed to get session object from the event payload"));

                if (!"complete".equals(session.getStatus())) {
                    throw new WebhookPayloadException("checkout session status is not complete");
                }

                try {
                    handleStripeCheckoutSessionEvent(session);
                } catch (WebhookPayloadException | WebhookEventException outer) {
                    try {
                        // refund subscription if we cannot handle the checkout session completed
                        // event. It is an edge-case event that should never happen, unless our
                        // internal data has gone corrupt somehow.
                        stripeApi.refundSubscription(session.getSubscription());
                    } catch (StripeException inner) {
                        val e = new RuntimeException("failed to cancel stripe subscription", inner);
                        e.addSuppressed(outer);
                        throw e;
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
                    .orElseThrow(() -> new WebhookPayloadException("failed to get subscription object from the event payload"));
                handleStripeSubscriptionEvent(stripeSubscription.getId());
                break;
            case "customer.deleted":
                val stripeCustomer = (com.stripe.model.Customer) event.getDataObjectDeserializer().getObject()
                    .orElseThrow(() -> new WebhookPayloadException("failed to get customer object from the event payload"));
                customerRepository.resetStripeId(stripeCustomer.getId());
                break;
        }
    }

    private void handleStripeCheckoutSessionEvent(@NonNull Session session) throws WebhookPayloadException, WebhookEventException {
        if (!"subscription".equals(session.getMode())) {
            throw new WebhookPayloadException("checkout session mode is not subscription");
        } else if (session.getSubscription() == null) {
            throw new WebhookPayloadException("checkout session subscription id is null");
        } else if (!"paid".equals(session.getPaymentStatus())) {
            throw new WebhookPayloadException("checkout session payment status is not paid");
        } else if (session.getCustomer() == null) {
            throw new WebhookPayloadException("customer id is missing from the checkout session");
        }

        final long subscriptionId;
        try {
            subscriptionId = parseLong(session.getClientReferenceId());
        } catch (NumberFormatException e) {
            throw new WebhookEventException("failed to parse the client reference id in checkout session");
        }

        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new WebhookEventException("failed to find subscription by checkout session's client reference id"));

        subscription.setProvidedId(session.getSubscription());
        val customer = subscription.getCustomer();
        if (!session.getCustomer().equals(customer.getStripeId())) {
            customer.setStripeId(session.getCustomer());
        }

        if (hasAnotherActiveSubscription(subscription)) {
            subscription.setRefunded(true);
            // immediately refund this subscription as user already owns another that is active.
            try {
                stripeApi.refundSubscription(session.getSubscription());
            } catch (StripeException e) {
                throw new RuntimeException("failed to refund stripe subscription", e);
            }
        } else {
            copySubscriptionDetailsFromStripeObject(subscription);
            customer.setTrialPeriodUsed(true);
        }

        customerRepository.save(customer);
        subscriptionRepository.save(subscription);
        evictIsSubscribedCache(subscription.getCustomer().getUserId());
    }

    private void handleStripeSubscriptionEvent(@NonNull String stripeSubscriptionId) throws WebhookPayloadException, WebhookEventException {
        val subscription = subscriptionRepository.findByProvidedId(stripeSubscriptionId)
            .orElseThrow(() -> new WebhookEventException("failed to find subscription corresponding to stripe object"));

        // always fetch subscription entity from Stripe API since retried (or delayed) webhook
        // events may contain stale data.
        copySubscriptionDetailsFromStripeObject(subscription);
        subscriptionRepository.save(subscription);
        evictIsSubscribedCache(subscription.getCustomer().getUserId());
    }

    private void copySubscriptionDetailsFromStripeObject(@NonNull Subscription subscription) throws WebhookPayloadException, WebhookEventException {
        final com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeApi.getSubscription(subscription.getProvidedId());
        } catch (StripeException e) {
            throw new RuntimeException("failed to get subscription object from stripe api", e);
        }

        if (stripeSubscription.getItems() == null
            || stripeSubscription.getItems().getData() == null
            || stripeSubscription.getItems().getData().size() != 1
            || stripeSubscription.getItems().getData().get(0) == null
            || stripeSubscription.getItems().getData().get(0).getPrice() == null) {
            throw new WebhookPayloadException("failed to get subscription price object from the event payload");
        }

        val stripePrice = stripeSubscription.getItems().getData().get(0).getPrice();
        if (stripePrice.getId() != null && !subscription.getPlan().getProvidedId().equals(stripePrice.getId())) {
            subscription.setPlan(
                subscriptionPlanRepository.findByProvidedId(SubscriptionPlan.Provider.STRIPE, stripePrice.getId())
                    .orElseThrow(() -> new WebhookEventException("updated provider plan id not recognised")));
        }

        if (stripeSubscription.getStartDate() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getStartDate());
        } else if (stripeSubscription.getCurrentPeriodStart() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getCurrentPeriodStart());
        }

        subscription.setPaymentPending("past_due".equals(stripeSubscription.getStatus()));
        subscription.setAutoRenewing(
            !("canceled".equals(stripeSubscription.getStatus()) || "unpaid".equals(stripeSubscription.getStatus())) &&
                !requireNonNullElse(stripeSubscription.getCancelAtPeriodEnd(), false)
        );

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
                if (stripeSubscription.getEndedAt() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getEndedAt());
                } else if (stripeSubscription.getCanceledAt() != null) {
                    subscription.setEndAtSeconds(stripeSubscription.getCanceledAt());
                }

                if (subscription.getEndAt() == null || subscription.getEndAt().isAfter(OffsetDateTime.now())) {
                    subscription.setEndAt(OffsetDateTime.now());
                }
                break;
        }
    }

    /**
     * Looks for an edge-case where a user can initiate two subscription purchase flows and then
     * complete them one after the other. In this case, the user will end-up with two active
     * subscriptions. Therefore, always check if the user obtained an active subscription sometime
     * after initiating and before completing a purchase flow.
     *
     * @return whether the {@link Customer} of the given {@code subscription} owns an active
     * subscription that is other the given {@code subscription}.
     */
    private boolean hasAnotherActiveSubscription(@NonNull Subscription subscription) {
        return subscriptionRepository.findActiveByCustomerUserId(subscription.getCustomer().getUserId())
            .map(s -> s.getId() != subscription.getId())
            .orElse(false);
    }

    /**
     * Returns the gift card with the given {@code giftCardCode} for the customer with the given
     * {@code customerId}.
     *
     * @param customerId   must not be {@literal null}.
     * @param giftCardCode must not be {@literal null}.
     * @return the requested gift card.
     * @throws GiftCardNotFoundException if the gift card doesn't exist or belong to the customer.
     */
    @NonNull
    public GiftCardResponse getGiftCard(@NonNull Long customerId, @NonNull String giftCardCode) throws GiftCardNotFoundException {
        val giftCard = giftCardRepository.findByCode(giftCardCode).orElseThrow(GiftCardNotFoundException::new);
        if (giftCard.getCustomer() != null && giftCard.getCustomer().getUserId() != customerId) {
            throw new GiftCardNotFoundException();
        }

        return buildGiftCardResponse(giftCard);
    }

    /**
     * Redeems an issued gift card with the given {@code giftCardCode} for the customer with the
     * given {@code customerId}.
     *
     * @param customerId   must not be {@literal null}.
     * @param giftCardCode must not be {@literal null}.
     * @return the created subscription on the successful redemption of the gift card.
     * @throws GiftCardNotFoundException      if the gift card doesn't exist or belong to the customer.
     * @throws GiftCardRedeemedException      if the gift card was already redeemed.
     * @throws GiftCardExpiredException       if the gift card has expired.
     * @throws DuplicateSubscriptionException if the customer has another active subscription.
     */
    @NonNull
    @Transactional(rollbackFor = Throwable.class)
    public SubscriptionResponseV2 redeemGiftCard(
        @NonNull Long customerId,
        @NonNull String giftCardCode
    ) throws GiftCardNotFoundException, GiftCardRedeemedException, GiftCardExpiredException, DuplicateSubscriptionException {
        if (subscriptionRepository.existsActiveByCustomerUserId(customerId)) {
            throw new DuplicateSubscriptionException();
        }

        val giftCard = giftCardRepository.findByCode(giftCardCode).orElseThrow(GiftCardNotFoundException::new);
        if (giftCard.getCustomer() != null && giftCard.getCustomer().getUserId() != customerId) {
            throw new GiftCardNotFoundException();
        }

        if (giftCard.getExpiresAt() != null && giftCard.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new GiftCardExpiredException();
        }

        if (giftCard.isRedeemed()) {
            throw new GiftCardRedeemedException();
        }

        if (giftCard.getCustomer() == null) {
            giftCard.setCustomer(getOrCreateCustomer(customerId));
        }

        giftCard.setRedeemed(true);
        giftCardRepository.save(giftCard);

        val now = OffsetDateTime.now();
        val then = now.plusHours(giftCard.getHourCredits());
        val subscription = subscriptionRepository.save(
            Subscription.builder()
                .customer(giftCard.getCustomer())
                .plan(giftCard.getPlan())
                .providedId(giftCardCode)
                .isPaymentPending(false)
                .isAutoRenewing(false)
                .isRefunded(false)
                .startAt(now)
                .endAt(then)
                .build());

        return buildSubscriptionResponse(subscription, null, null);
    }

    /**
     * @return if the user with given {@code userId} owns an active subscription.
     */
    @Override
    @Cacheable(cacheNames = SubscriptionBeans.CACHE_NAME, key = "'isSubscribed:' + #userId")
    public boolean isUserSubscribed(@NonNull Long userId) {
        return subscriptionRepository.existsActiveByCustomerUserId(userId);
    }

    /**
     * Creates a new Stripe Customer Portal session for the given {@code customerId} and returns its
     * URL.
     *
     * @param customerId a not {@literal null} id of the customer.
     * @param returnUrl  a not {@literal null} redirect url for exiting the customer portal.
     * @return a not {@literal null} {@link StripeCustomerPortalUrlResponse}.
     * @throws StripeCustomerPortalUrlException if the customer with given {@code customerId}
     *                                          doesn't exist on Stripe.
     */
    @NonNull
    public StripeCustomerPortalUrlResponse getStripeCustomerPortalUrl(
        @NonNull Long customerId,
        @NonNull String returnUrl
    ) throws StripeCustomerPortalUrlException {
        val customer = customerRepository.findById(customerId).orElseThrow(StripeCustomerPortalUrlException::new);
        if (customer.getStripeId() == null) {
            throw new StripeCustomerPortalUrlException();
        }

        try {
            val session = stripeApi.createCustomerPortalSession(customer.getStripeId(), returnUrl);
            return StripeCustomerPortalUrlResponse.builder()
                .url(session.getUrl())
                .build();
        } catch (StripeException e) {
            throw new RuntimeException("stripe api error", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    @Transactional(rollbackFor = Throwable.class)
    public void onUserDeleted(@NonNull AccountServiceContract.UserDeletedEvent event) {
        customerRepository.findById(event.getUserId())
            .map(Customer::getStripeId)
            .ifPresent(stripeCustomerId -> {
                try {
                    stripeApi.resetCustomerNameAndEmail(stripeCustomerId);
                } catch (StripeException e) {
                    throw new RuntimeException(e);
                }
            });

        subscriptionRepository.findActiveByCustomerUserId(event.getUserId())
            .ifPresent(subscription -> {
                subscription.setEndAt(OffsetDateTime.now());
                subscriptionRepository.save(subscription);
            });
    }

    void updateForeignExchangeRates() {
        exchangeRatesProvider.maybeUpdateRates();
    }

    @Transactional(rollbackFor = Throwable.class)
    public void performGarbageCollection() {
        val deleteBefore = OffsetDateTime.now().minus(subscriptionConfig.getRemoveIncompleteSubscriptionsAfter());
        subscriptionRepository.deleteAllIncompleteCreatedBefore(deleteBefore);
    }

    private void evictIsSubscribedCache(long userId) {
        cache.evictIfPresent(String.format("isSubscribed:%d", userId));
    }

    @NonNull
    private static SubscriptionPlanResponse buildSubscriptionPlanResponse(@NonNull SubscriptionPlan plan, String currencyCode, Double exchangeRate) {
        val convertedPrice = exchangeRate == null ? null : (plan.getPriceInIndianPaise() / 100) * exchangeRate;
        return SubscriptionPlanResponse.builder()
            .id(plan.getId())
            .provider(plan.getProvider().name().toLowerCase())
            .billingPeriodMonths(plan.getBillingPeriodMonths())
            .trialPeriodDays(plan.getTrialPeriodDays())
            .priceInIndianPaise(plan.getPriceInIndianPaise())
            .priceInRequestedCurrency(convertedPrice)
            .requestedCurrencyCode(convertedPrice == null ? null : currencyCode)
            .googlePlaySubscriptionId(
                plan.getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                    ? plan.getProvidedId()
                    : null)
            .build();
    }

    @NonNull
    private static SubscriptionResponseV2 buildSubscriptionResponse(@NonNull Subscription subscription, String currencyCode, Double exchangeRate) {
        return SubscriptionResponseV2.builder()
            .id(subscription.getId())
            .plan(buildSubscriptionPlanResponse(subscription.getPlan(), currencyCode, exchangeRate))
            .isActive(subscription.isActive())
            // redundant sanity check, only an active subscription can have a pending payment.
            .isPaymentPending(subscription.isActive() && subscription.isPaymentPending())
            .startedAt(subscription.getStartAt())
            .endedAt(
                subscription.getEndAt() != null && subscription.getEndAt().isBefore(OffsetDateTime.now())
                    ? subscription.getEndAt() : null)
            .isAutoRenewing(subscription.isActive() && subscription.isAutoRenewing())
            .renewsAt(subscription.isActive() ? subscription.getEndAt() : null)
            .isRefunded(subscription.isRefunded() ? true : null)
            .googlePlayPurchaseToken(
                subscription.isActive() && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                    ? subscription.getProvidedId() : null)
            .giftCardCode(
                subscription.getPlan().getProvider() == SubscriptionPlan.Provider.GIFT_CARD
                    ? subscription.getProvidedId()
                    : null)
            .build();
    }

    @NonNull
    private static GiftCardResponse buildGiftCardResponse(@NonNull GiftCard giftCard) {
        return GiftCardResponse.builder()
            .code(giftCard.getCode())
            .hourCredits(giftCard.getHourCredits())
            .isRedeemed(giftCard.isRedeemed())
            .expiresAt(giftCard.getExpiresAt())
            .build();
    }
}
