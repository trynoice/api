package com.trynoice.api.subscription.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data transfer object to send subscription flow details back to the SubscriptionController.
 */
@Data
@NoArgsConstructor
public class SubscriptionFlowResponseV2 {

    @JsonIgnore
    private SubscriptionResponseV2 subscription;

    @Schema(required = true, description = "id of the newly created subscription")
    private long subscriptionId;

    @Schema(description = "Checkout url for billing this subscription. Only present if the provider is 'stripe'")
    private String stripeCheckoutSessionUrl;
}
