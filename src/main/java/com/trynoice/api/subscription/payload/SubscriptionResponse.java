package com.trynoice.api.subscription.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Subscription")
public class SubscriptionResponse {

    @Schema(required = true, description = "id of the subscription purchase")
    @NonNull
    private Long id;

    @Schema(required = true, description = "plan associated with this subscription purchase")
    @NonNull
    private SubscriptionPlanResponse plan;

    @Schema(required = true, description = "whether this subscription purchase is currently active")
    @NonNull
    private Boolean isActive;

    @Schema(required = true, description = "whether a payment for this subscription purchase is currently pending")
    @NonNull
    private Boolean isPaymentPending;

    @Schema(type = "integer", format = "int64", description = "epoch millis when the subscription started")
    private OffsetDateTime startedAt;

    @Schema(type = "integer", format = "int64", description = "epoch millis when the subscription ended")
    private OffsetDateTime endedAt;

    @Schema(required = true, description = "whether the subscription will renew at the end of this billing " +
        "cycle. if false, it implies that the subscription will end at the end of current billing cycle.")
    @NonNull
    private Boolean isAutoRenewing;

    @Schema(description = "whether the subscription was cancelled and its amount refunded.")
    private Boolean isRefunded;

    @Schema(type = "integer", format = "int64", description = "epoch millis when the current billing cycle ends " +
        "and the next one starts. always present unless the subscription is inactive.")
    private OffsetDateTime renewsAt;

    @Schema(description = "Stripe customer portal URL to manage subscriptions. only present when the subscription " +
        "is active and provided by Stripe.")
    private String stripeCustomerPortalUrl;

    @Schema(description = "purchase token corresponding to this subscription purchase. only present when the " +
        "subscription is active and provided by Google Play")
    private String googlePlayPurchaseToken;
}
