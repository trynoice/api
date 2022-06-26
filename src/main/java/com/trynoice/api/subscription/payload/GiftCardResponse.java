package com.trynoice.api.subscription.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.OffsetDateTime;

/**
 *
 */
@Data
@Builder
@AllArgsConstructor
@Schema(name = "GiftCard")
public class GiftCardResponse {

    @Schema(required = true, description = "code of the gift card.")
    @NonNull
    private String code;

    @Schema(required = true, description = "duration (in hours) of the subscription that the gift card provides on redemption.")
    private int hourCredits;

    @Schema(required = true, description = "whether the gift card has been redeemed.")
    @NonNull
    private Boolean isRedeemed;

    @Schema(type = "integer", format = "int64", description = "optional epoch millis when the gift card expires.")
    private OffsetDateTime expiresAt;
}
