package com.trynoice.api.subscription.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data transfer object to send subscription flow details back to the SubscriptionController.
 */
@Data
@NoArgsConstructor
public class SubscriptionFlowResult {

    @NonNull
    private Long subscriptionId;

    private String stripeCheckoutSessionUrl;
}
