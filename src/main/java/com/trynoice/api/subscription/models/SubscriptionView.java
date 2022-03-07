package com.trynoice.api.subscription.models;

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
public class SubscriptionView {

    @Schema(required = true, description = "id of the subscription purchase")
    @NonNull
    private Long id;

    @Schema(required = true, description = "plan associated with this subscription purchase")
    @NonNull
    private SubscriptionPlanView plan;

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

    @Schema(type = "integer", format = "int64", description = "epoch millis when the next billing cycle starts (only present if isActive = T)")
    private OffsetDateTime renewsAt;

    @Schema(description = "Stripe customer portal URL to manage subscriptions (only present if provider = stripe and isActive = T)")
    private String stripeCustomerPortalUrl;
}
