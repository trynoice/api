package com.trynoice.api.subscription.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data transfer object to send subscription plan details back to the controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanView {

    @NonNull
    private Short id;

    @NonNull
    private String provider;

    @NonNull
    private Short billingPeriodMonths;

    @NonNull
    private String priceInr;
}
