package com.trynoice.api.subscription.models;

import com.trynoice.api.platform.validation.annotations.HttpUrl;
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

    @NotNull
    @Min(1)
    private Short planId;

    @HttpUrl
    private String successUrl;

    @HttpUrl
    private String cancelUrl;
}
