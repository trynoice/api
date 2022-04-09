package com.trynoice.api.subscription.models;

import com.trynoice.api.platform.validation.annotations.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

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

    @Schema(description = "redirect url when the user completes the checkout session. it is only required for Stripe plans")
    @HttpUrl
    private String successUrl;

    @Schema(description = "redirect url when the user cancels the checkout session. it is only required for Stripe plans")
    @HttpUrl
    private String cancelUrl;

    /**
     * Sets the {@link SubscriptionFlowParams#successUrl} to the given {@code url} after replacing
     * {@code {subscriptionId}} template string with the given {@code subscriptionId}.
     */
    public void setSuccessUrl(@NotNull String url, long subscriptionId) {
        this.successUrl = injectSubscriptionId(url, subscriptionId);
    }

    /**
     * Sets the {@link SubscriptionFlowParams#cancelUrl} to the given {@code url} after replacing
     * {@code {subscriptionId}} template string with the given {@code subscriptionId}.
     */
    public void setCancelUrl(@NonNull String url, long subscriptionId) {
        this.cancelUrl = injectSubscriptionId(url, subscriptionId);
    }

    @NonNull
    private String injectSubscriptionId(@NonNull String url, long subscriptionId) {
        return url.replaceAll("(\\{|%7B)subscriptionId(}|%7D)", String.valueOf(subscriptionId));
    }
}
