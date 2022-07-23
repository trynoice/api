package com.trynoice.api.subscription.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class StripeCustomerPortalUrlResponse {

    @Schema(required = true, description = "short-lived URL of the session that provides access to the customer portal.")
    @NonNull
    private String url;
}
