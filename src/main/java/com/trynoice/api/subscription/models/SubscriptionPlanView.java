package com.trynoice.api.subscription.models;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(required = true, description = "id of the subscription plan")
    @NonNull
    private Short id;

    @Schema(required = true, description = "provider of the subscription plan, e.g. google_play or stripe")
    @NonNull
    private String provider;

    @Schema(required = true, description = "number of months included in a single billing period, e.g. 1 or 3")
    @NonNull
    private Short billingPeriodMonths;

    @Schema(required = true, description = "currency formatted string showing plan's price in INR, e.g. '₹225'")
    @NonNull
    private String priceInr;
}
