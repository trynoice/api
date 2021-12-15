package com.trynoice.api.identity.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data transfer object to send auth credentials back to the controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredentials {

    @Schema(required = true, description = "rotated refresh token")
    @NonNull
    private String refreshToken;

    @Schema(required = true, description = "new access token")
    @NonNull
    private String accessToken;
}
