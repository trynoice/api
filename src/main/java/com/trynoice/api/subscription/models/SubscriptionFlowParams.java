package com.trynoice.api.subscription.models;

import com.trynoice.api.platform.validation.annotations.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * A data transfer object to hold the body of create-subscription requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionFlowParams {

    @Schema(required = true, description = "id of the subscription plan selected by the user")
    @NotNull
    @Min(1)
    private Short planId;

    @Schema(description = "redirect url when the user completes the checkout session (required only for Stripe plans)")
    @HttpUrl
    private String successUrl;

    @Schema(description = "redirect url when the user cancels the checkout session (required only for Stripe plans)")
    @HttpUrl
    private String cancelUrl;
}
