package com.trynoice.api.subscription.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data transfer object to send subscription flow details back to the SubscriptionController.
 */
@Data
@NoArgsConstructor
public class SubscriptionFlowResult {

    @Schema(required = true, description = "the newly created subscription")
    @NonNull
    private SubscriptionResult subscription;

    @Schema(description = "Checkout url for billing this subscription. Only present if the provider is 'stripe'")
    private String stripeCheckoutSessionUrl;
}
