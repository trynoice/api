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
public class SubscriptionView {

    @Schema(required = true, description = "id of the subscription purchase")
    @NonNull
    private Long id;

    @Schema(required = true, description = "plan associated with this subscription purchase")
    @NonNull
    private SubscriptionPlanView plan;

    @Schema(required = true, description = "if the subscription is currently on-going")
    @NonNull
    private Boolean isActive;

    @Schema(required = true, description = "if the subscription has a pending invoice that needs to be paid")
    @NonNull
    private Boolean isPaymentPending;

    @Schema(required = true, description = "subscription start timestamp (ISO-8601 format)")
    @NonNull
    private LocalDateTime startedAt;

    @Schema(description = "subscription end timestamp (ISO-8601 format) if the subscription has ended (isActive = false)")
    private LocalDateTime endedAt;
}
