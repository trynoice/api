package com.trynoice.api.subscription.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Subscription")
public class SubscriptionView {

    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACTIVE = "active";

    @Schema(required = true, description = "id of the subscription purchase")
    @NonNull
    private Long id;

    @Schema(required = true, description = "plan associated with this subscription purchase")
    @NonNull
    private SubscriptionPlanView plan;

    @Schema(
        required = true,
        allowableValues = {STATUS_INACTIVE, STATUS_PENDING, STATUS_ACTIVE},
        description = "the current status of the subscription.\n\n" +
            "- `inactive`: The subscription is currently inactive. It may be due to a pending payment " +
            "when subscription flow is started. Otherwise, it indicates the subscription has expired.\n" +
            "- `pending`: the payment for the subscription is pending, but the user has access to its entitlements.\n" +
            "- `active`: the subscription is currently active, and the user have access to all its entitlements."
    )
    @NonNull
    private String status;

    @Schema(description = "subscription start timestamp (ISO-8601 format) if the subscription is active (status = pending/active)")
    private LocalDateTime startedAt;

    @Schema(description = "subscription end timestamp (ISO-8601 format) if the subscription has ended (status = ended)")
    private LocalDateTime endedAt;

    @Schema(description = "Stripe customer portal URL to manage subscriptions (only present if provider = stripe and status = active)")
    private String stripeCustomerPortalUrl;
}
