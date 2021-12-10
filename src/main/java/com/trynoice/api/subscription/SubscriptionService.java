package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.trynoice.api.identity.AccountService;
import com.trynoice.api.identity.AuthUserRepository;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import lombok.NonNull;
import lombok.val;
import org.postgresql.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuthUserRepository authUserRepository;
    private final ObjectMapper objectMapper;
    private final AndroidPublisherApi androidPublisherApi;

    @Autowired
    SubscriptionService(
        @NonNull SubscriptionConfiguration subscriptionConfig,
        @NonNull SubscriptionPlanRepository subscriptionPlanRepository,
        @NonNull SubscriptionRepository subscriptionRepository,
        @NonNull AuthUserRepository authUserRepository,
        @NonNull ObjectMapper objectMapper,
        @NonNull AndroidPublisherApi androidPublisherApi
    ) {
        this.subscriptionConfig = subscriptionConfig;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.authUserRepository = authUserRepository;
        this.objectMapper = objectMapper;
        this.androidPublisherApi = androidPublisherApi;
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
    @Transactional
    void handleGooglePlayWebhookEvent(@NonNull JsonNode payload) throws SubscriptionWebhookEventException {
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

        val subscriptionId = subscriptionNotification.at("/subscriptionId");
        if (!subscriptionId.isTextual()) {
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
                subscriptionId.asText(),
                purchaseToken.asText());
        } catch (IOException e) {
            throw new SubscriptionWebhookEventException("failed to retrieve purchase data from google play", e);
        }

        final long ownerId;
        try {
            ownerId = parseLong(purchase.getObfuscatedExternalAccountId());
        } catch (NumberFormatException e) {
            throw new SubscriptionWebhookEventException("failed to parse obfuscated account id in subscription purchase", e);
        }

        val owner = authUserRepository.findActiveById(ownerId)
            .orElseThrow(() -> new SubscriptionWebhookEventException("owner account with id '" + ownerId + "' doesn't exist"));

        val plan = subscriptionPlanRepository.findActiveByProviderPlanId(SubscriptionPlan.Provider.GOOGLE_PLAY, subscriptionId.asText())
            .orElseThrow(() -> new SubscriptionWebhookEventException("failed to subscription plan with id '" + subscriptionId.asText() + "'"));

        val subscription = subscriptionRepository.findActiveByProviderSubscriptionId(purchaseToken.asText())
            .orElseGet(() -> Subscription.builder()
                .owner(owner)
                .plan(plan)
                .providerSubscriptionId(purchaseToken.asText())
                .startAt(LocalDateTime.MAX)
                .build());

        if (subscription.getStartAt().isEqual(LocalDateTime.MAX)) {
            subscription.setStartAt(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(purchase.getStartTimeMillis()), ZoneId.systemDefault()));
        }

        // https://developer.android.com/google/play/billing/subscriptions#lifecycle
        // tldr; if the expiry time is in the future, the user must have the subscription
        // entitlements. Their payment might be pending and in that case, if the expiry is in the
        // future, the user is in a grace-period (retains their entitlements). If the expiry is in
        // the past, the user's account is on hold, and they lose their entitlements. When the user
        // is in their grace-period, they should be notified about the pending payment.
        val endAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(purchase.getExpiryTimeMillis()), ZoneId.systemDefault());
        subscription.setEndAt(endAt);
        if (endAt.isAfter(LocalDateTime.now())) {
            subscription.setStatus(Integer.valueOf(0).equals(purchase.getPaymentState()) ? Subscription.Status.PENDING : Subscription.Status.ACTIVE);
        } else {
            subscription.setStatus(Subscription.Status.INACTIVE);
        }

        subscriptionRepository.save(subscription);

        // invalidate old subscription.
        // https://developer.android.com/google/play/billing/subscriptions#upgrade-downgrade
        val linkedPurchaseToken = purchase.getLinkedPurchaseToken();
        if (linkedPurchaseToken != null) {
            val linkedSubscription = subscriptionRepository.findActiveByProviderSubscriptionId(linkedPurchaseToken).orElse(null);
            if (linkedSubscription != null && linkedSubscription.getStatus() != Subscription.Status.INACTIVE) {
                linkedSubscription.setStatus(Subscription.Status.INACTIVE);
                linkedSubscription.setEndAt(LocalDateTime.now());
                subscriptionRepository.save(linkedSubscription);
            }
        }

        if (!Integer.valueOf(1).equals(purchase.getAcknowledgementState())) {
            try {
                androidPublisherApi.acknowledgePurchase(
                    subscriptionConfig.getAndroidApplicationId(),
                    subscriptionId.asText(),
                    purchaseToken.asText());
            } catch (IOException e) {
                throw new SubscriptionWebhookEventException("failed to acknowledge subscription purchase", e);
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
