package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.trynoice.api.contracts.SoundSubscriptionServiceContract;
import com.trynoice.api.contracts.SubscriptionAccountServiceContract;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.exceptions.WebhookEventException;
import com.trynoice.api.subscription.exceptions.WebhookPayloadException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.subscription.models.SubscriptionView;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNullElse;

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

        return StreamSupport.stream(plans.spliterator(), false)
            .map(SubscriptionService::buildSubscriptionPlanView)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * <p>
     * Initiates the subscription flow by creating a new {@code incomplete} subscription entity and
     * returns it with the result.</p>
     *
     * <p>
     * If the requested plan is provided by {@link SubscriptionPlan.Provider#STRIPE}, it also
     * returns a non-null {@link SubscriptionFlowResult#getStripeCheckoutSessionUrl() checkout
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
                throw new ConstraintViolationException("'successUrl' is required for 'stripe' plans", null);
            }

            if (params.getCancelUrl() == null) {
                throw new ConstraintViolationException("'cancelUrl' is required for 'stripe' plans", null);
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
        result.setSubscription(buildSubscriptionView(subscription, null));
        if (plan.getProvider() != SubscriptionPlan.Provider.STRIPE) {
            return result;
        }

        final Session checkoutSession;
        try {
            val toReplaceRegex = "(\\{|%7B)subscriptionId(}|%7D)";
            val subscriptionIdStr = String.valueOf(subscription.getId());
            checkoutSession = stripeApi.createCheckoutSession(
                params.getSuccessUrl().replaceAll(toReplaceRegex, subscriptionIdStr),
                params.getCancelUrl().replaceAll(toReplaceRegex, subscriptionIdStr),
                plan.getProviderPlanId(),
                subscriptionIdStr,
                customer.getStripeId() == null
                    ? accountServiceContract.findEmailByUser(customerId).orElse(null)
                    : null,
                customer.getStripeId(),
                customer.isTrialPeriodUsed() ? null : (long) plan.getTrialPeriodDays());
        } catch (StripeException e) {
            throw new RuntimeException("failed to create stripe checkout session", e);
        }

        if (checkoutSession.getSubscription() != null) {
            subscription.setProviderSubscriptionId(checkoutSession.getSubscription());
            subscriptionRepository.save(subscription);
        }

        result.setStripeCheckoutSessionUrl(checkoutSession.getUrl());
        return result;
    }

    /**
     * Lists a page of subscriptions purchased by a customer with given {@code customerId}. Each
     * page contains at-most 20 entries. If the {@code pageNumber} is higher than the available
     * pages, it returns an empty list. If {@code onlyActive} is {@literal true}, it lists the
     * currently active subscription purchase (at most one).
     *
     * @param customerId      id of the customer (user) that purchased the subscriptions.
     * @param onlyActive      return only the active subscription (if any).
     * @param stripeReturnUrl optional redirect URL on exiting Stripe Customer portal (only used
     *                        when an active subscription exists and is provided by Stripe).
     * @param pageNumber      a not {@literal null} 0-indexed page number.
     * @return a list of subscription purchased by the given {@code customerId}.
     */
    @NonNull
    List<SubscriptionView> listSubscriptions(
        @NonNull Long customerId,
        @NonNull Boolean onlyActive,
        String stripeReturnUrl,
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
     * Get a subscription purchased by a customer by its {@code subscriptionId}.
     *
     * @param customerId      must not be {@literal null}.
     * @param subscriptionId  must not be {@literal null}.
     * @param stripeReturnUrl optional redirect URL for exiting Stripe customer portal.
     * @return the requested subscription, guaranteed to be not {@literal null}.
     * @throws SubscriptionNotFoundException if such a subscription doesn't exist.
     */
    @NonNull
    public SubscriptionView getSubscription(
        @NonNull Long customerId,
        @NonNull Long subscriptionId,
        String stripeReturnUrl
    ) throws SubscriptionNotFoundException {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException("subscription with given id doesn't exist"));

        if (!customerId.equals(subscription.getCustomer().getUserId())) {
            throw new SubscriptionNotFoundException("subscription with given id is not owned by the customer");
        }

        if (subscription.getStartAt() == null) {
            throw new SubscriptionNotFoundException("subscription has not started yet");
        }

        return buildSubscriptionView(
            subscription,
            subscription.isActive() && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE
                ? createStripeCustomerSession(subscription.getCustomer(), stripeReturnUrl)
                : null);
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
                    // cancel at the end of current billing period to match Google Play Subscriptions behaviour.
                    stripeApi.cancelSubscription(subscription.getProviderSubscriptionId(), false);
                } catch (StripeException e) {
                    throw new RuntimeException("stripe api error", e);
                }

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
     * subscription purchase from the Google API.</p>
     *
     * @param payload event payload.
     * @throws WebhookPayloadException on failing to correctly parse the event payload.
     * @throws WebhookEventException   on failing to correctly process the event.
     * @see <a href="https://developer.android.com/google/play/billing/rtdn-reference#sub">Google
     * Play real-time developer notifications reference</a>
     * @see <a href="https://developer.android.com/google/play/billing/subscriptions">Google Play
     * subscription documentation</a>
     * @see <a href="https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions">
     * Android Publisher REST API reference</a>
     */
    @Transactional(rollbackFor = Throwable.class)
    public void handleGooglePlayWebhookEvent(
        @NonNull JsonNode payload
    ) throws WebhookPayloadException, WebhookEventException {
        val data = payload.at("/message/data");
        if (!data.isTextual()) {
            throw new WebhookPayloadException("'message.data' field is missing or invalid in the payload");
        }

        final JsonNode event;
        try {
            event = objectMapper.readTree(Base64.getDecoder().decode(data.asText()));
        } catch (IOException | IllegalArgumentException e) {
            throw new WebhookPayloadException("failed to decode base64 or json in 'message.data'", e);
        }

        val subscriptionNotification = event.at("/subscriptionNotification");
        if (!subscriptionNotification.isObject()) {
            // this is not a subscription notification if the object is missing. return normally.
            return;
        }

        val notificationType = subscriptionNotification.at("/notificationType");
        if (!notificationType.isInt()) {
            throw new WebhookPayloadException("'notificationType' is missing or invalid in 'subscriptionNotification'");
        }

        val subscriptionPlanId = subscriptionNotification.at("/subscriptionId");
        if (!subscriptionPlanId.isTextual()) {
            throw new WebhookPayloadException("'subscriptionId' is missing or invalid in 'subscriptionNotification'");
        }

        val purchaseToken = subscriptionNotification.at("/purchaseToken");
        if (!purchaseToken.isTextual()) {
            throw new WebhookPayloadException("'purchaseToken' is missing or invalid in 'subscriptionNotification'");
        }

        final SubscriptionPurchase purchase;
        try {
            purchase = androidPublisherApi.getSubscriptionPurchase(
                subscriptionConfig.getAndroidApplicationId(),
                subscriptionPlanId.asText(),
                purchaseToken.asText());
        } catch (IOException e) {
            throw new RuntimeException("failed to retrieve purchase data from google play", e);
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
        // objects using purchase tokens (provider subscription id).

        val subscription = notificationType.asInt() == 4 // = new purchase
            ? subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new WebhookEventException("failed to find subscription entity for this purchase"))
            : subscriptionRepository.findByProviderSubscriptionId(purchaseToken.asText())
            .orElseGet(() -> purchase.getLinkedPurchaseToken() != null
                ? subscriptionRepository.findByProviderSubscriptionId(purchase.getLinkedPurchaseToken()).orElse(null)
                : null);

        if (subscription == null) {
            // subscription is missing; the purchase token may have been invalidated during by an
            // upgrade/downgrade.
            return;
        }

        if (!subscriptionPlanId.asText().equals(subscription.getPlan().getProviderPlanId())) {
            subscription.setPlan(
                subscriptionPlanRepository.findByProviderPlanId(
                        SubscriptionPlan.Provider.GOOGLE_PLAY, subscriptionPlanId.asText())
                    .orElseThrow(() -> new WebhookEventException("unknown provider plan id")));
        }

        // https://developer.android.com/google/play/billing/subscriptions#lifecycle
        // tldr; if the expiry time is in the future, the user must have the subscription
        // entitlements. Their payment might be pending and in that case, if the expiry is in the
        // future, the user is in a grace-period (retains their entitlements). If the expiry is in
        // the past, the user's account is on hold, and they lose their entitlements. When the user
        // is in their grace-period, they should be notified about the pending payment.
        //
        // Moreover, linked purchase token should be ignored since we're not using purchase tokens
        // to identify the internal subscription entity. Both the upgraded and the old subscription
        // will have the same obfuscated account id, and thus, its linked purchase token will be
        // automatically overwritten when the upgraded subscription's notification arrives.
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

        subscription.setAutoRenewing(requireNonNullElse(purchase.getAutoRenewing(), true));
        subscriptionRepository.save(subscription);

        if (!subscription.getCustomer().isTrialPeriodUsed()) {
            subscription.getCustomer().setTrialPeriodUsed(true);
            customerRepository.save(subscription.getCustomer());
        }

        if (!Integer.valueOf(1).equals(purchase.getAcknowledgementState())) {
            try {
                androidPublisherApi.acknowledgePurchase(
                    subscriptionConfig.getAndroidApplicationId(),
                    subscriptionPlanId.asText(),
                    purchaseToken.asText());
            } catch (IOException e) {
                throw new RuntimeException("failed to acknowledge subscription purchase", e);
            }
        }
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
                        // cancel subscription if we cannot handle the checkout session completed
                        // event. It is an edge-case event that should never happen, unless our
                        // internal data has gone corrupt somehow.
                        stripeApi.cancelSubscription(session.getSubscription(), true);
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
                handleStripeSubscriptionEvent(stripeSubscription);
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
            .orElseThrow(() -> {
                val errMsg = String.format("subscription with id '%d' doesn't exist", subscriptionId);
                return new WebhookEventException(errMsg);
            });

        copySubscriptionDetailsFromStripeObject(subscription, session.getSubscription());
        subscriptionRepository.save(subscription);

        val customer = subscription.getCustomer();
        if (customer.getStripeId() == null || !customer.getStripeId().equals(session.getCustomer())) {
            customer.setStripeId(session.getCustomer());
        }

        customer.setTrialPeriodUsed(true);
        customerRepository.save(customer);
    }

    private void handleStripeSubscriptionEvent(
        @NonNull com.stripe.model.Subscription stripeSubscription
    ) throws WebhookPayloadException, WebhookEventException {
        val subscription = subscriptionRepository.findByProviderSubscriptionId(stripeSubscription.getId())
            .orElseThrow(() -> {
                val errMsg = String.format("subscription with providerSubscriptionId '%s' doesn't exist", stripeSubscription.getId());
                return new WebhookEventException(errMsg);
            });

        // always fetch subscription entity from Stripe API since retried (or delayed) webhook
        // events may contain stale data.
        copySubscriptionDetailsFromStripeObject(subscription, stripeSubscription.getId());
        subscriptionRepository.save(subscription);
    }

    private void copySubscriptionDetailsFromStripeObject(
        @NonNull Subscription subscription,
        @NonNull String stripeSubscriptionId
    ) throws WebhookPayloadException, WebhookEventException {
        final com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeApi.getSubscription(stripeSubscriptionId);
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
        if (stripePrice.getId() != null && !subscription.getPlan().getProviderPlanId().equals(stripePrice.getId())) {
            subscription.setPlan(
                subscriptionPlanRepository.findByProviderPlanId(SubscriptionPlan.Provider.STRIPE, stripePrice.getId())
                    .orElseThrow(() -> new WebhookEventException("updated provider plan id not recognised")));
        }

        if (stripeSubscription.getStartDate() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getStartDate());
        } else if (stripeSubscription.getCurrentPeriodStart() != null) {
            subscription.setStartAtSeconds(stripeSubscription.getCurrentPeriodStart());
        }

        subscription.setPaymentPending("past_due".equals(stripeSubscription.getStatus()));
        subscription.setAutoRenewing(!requireNonNullElse(stripeSubscription.getCancelAtPeriodEnd(), false));
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
                subscription.setEndAt(OffsetDateTime.now());
                break;
        }
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
            .isActive(subscription.isActive())
            // redundant sanity check, only an active subscription can have a pending payment.
            .isPaymentPending(subscription.isActive() && subscription.isPaymentPending())
            .startedAt(subscription.getStartAt())
            .endedAt(
                subscription.getEndAt() != null && subscription.getEndAt().isBefore(OffsetDateTime.now())
                    ? subscription.getEndAt() : null)
            .isAutoRenewing(subscription.isActive() && subscription.isAutoRenewing())
            .renewsAt(subscription.isActive() ? subscription.getEndAt() : null)
            .stripeCustomerPortalUrl(
                subscription.isActive() && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE
                    ? stripeCustomerPortalUrl : null)
            .googlePlayPurchaseToken(
                subscription.isActive() && subscription.getPlan().getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                    ? subscription.getProviderSubscriptionId() : null)
            .build();
    }
}
