package com.trynoice.api.subscription.models;

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

    @NonNull
    private Long id;

    @NonNull
    private SubscriptionPlanView plan;

    @NonNull
    private Boolean isActive, isPaymentPending;

    @NonNull
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
