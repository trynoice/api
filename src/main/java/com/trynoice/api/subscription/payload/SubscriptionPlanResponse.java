package com.trynoice.api.subscription.payload;

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
@Schema(name = "SubscriptionPlan")
public class SubscriptionPlanResponse {

    @Schema(required = true, description = "id of the subscription plan")
    @NonNull
    private Short id;

    @Schema(
        required = true,
        allowableValues = {"google_play", "stripe"},
        description = "provider of the subscription plan (case-insensitive)"
    )
    @NonNull
    private String provider;

    @Schema(required = true, description = "number of months included in a single billing period, e.g. 1 or 3")
    @NonNull
    private Short billingPeriodMonths;

    @Schema(required = true, description = "number of days included as the trial period with the plan")
    @NonNull
    private Short trialPeriodDays;

    @Schema(required = true, description = "price of the plan in Indian Paise (INR * 100)")
    @NonNull
    private Integer priceInIndianPaise;

    @Schema(description = "an optional converted and formatted price string if currency was provided in the " +
        "request parameters. It may be absent despite specifying the currency in the request parameters.")
    private Double priceInRequestedCurrency;

    @Schema(description = "an optional currency code for the priceInRequestedCurrency. It is only present if " +
        "priceInRequestedCurrency is also present.")
    private String requestedCurrencyCode;

    @Schema(description = "Google Play assigned id of the subscription plan. Only present if provider is 'google_play'")
    private String googlePlaySubscriptionId;
}
