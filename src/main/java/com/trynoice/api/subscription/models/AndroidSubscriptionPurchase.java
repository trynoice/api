package com.trynoice.api.subscription.models;

import com.google.api.client.util.DateTime;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.Objects;

/**
 * A simplified model that streamlines {@link SubscriptionPurchaseV2} for our use-case.
 */
@Data
@Builder
@With
@AllArgsConstructor
public class AndroidSubscriptionPurchase {

    private final String productId;
    private final String subscriptionState;
    private final boolean isAutoRenewing;
    private final boolean isPaymentPending;
    private final String linkedPurchaseToken;
    private final Long startTimeMillis;
    private final Long expiryTimeMillis;
    private final String obfuscatedExternalAccountId;
    private final boolean isAcknowledged;
    private final boolean isTestPurchase;

    public AndroidSubscriptionPurchase(SubscriptionPurchaseV2 purchaseV2) {
        // https://developer.android.com/google/play/billing/compatibility#subscriptionpurchasev2-fields

        this.productId = purchaseV2.getLineItems() == null ? null : purchaseV2.getLineItems()
            .stream()
            .map(SubscriptionPurchaseLineItem::getProductId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        this.subscriptionState = purchaseV2.getSubscriptionState();
        this.isPaymentPending = "SUBSCRIPTION_STATE_PENDING".equals(purchaseV2.getSubscriptionState())
            || "SUBSCRIPTION_STATE_IN_GRACE_PERIOD".equals(purchaseV2.getSubscriptionState())
            || "SUBSCRIPTION_STATE_ON_HOLD".equals(purchaseV2.getSubscriptionState());

        this.isAutoRenewing = purchaseV2.getLineItems() != null && purchaseV2.getLineItems()
            .stream()
            .anyMatch(i -> i.getAutoRenewingPlan() != null && Boolean.TRUE.equals(i.getAutoRenewingPlan().getAutoRenewEnabled()));

        this.linkedPurchaseToken = purchaseV2.getLinkedPurchaseToken();
        this.startTimeMillis = purchaseV2.getStartTime() != null
            ? DateTime.parseRfc3339(purchaseV2.getStartTime()).getValue()
            : null;

        this.expiryTimeMillis = purchaseV2.getLineItems() == null ? null : purchaseV2.getLineItems()
            .stream()
            .map(i -> i.getExpiryTime() == null ? null : DateTime.parseRfc3339(i.getExpiryTime()).getValue())
            .filter(Objects::nonNull)
            .max(Long::compare)
            .orElse(null);

        this.obfuscatedExternalAccountId = purchaseV2.getExternalAccountIdentifiers() != null
            ? purchaseV2.getExternalAccountIdentifiers().getObfuscatedExternalAccountId()
            : null;

        this.isAcknowledged = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(purchaseV2.getAcknowledgementState());
        this.isTestPurchase = purchaseV2.getTestPurchase() != null;
    }
}
