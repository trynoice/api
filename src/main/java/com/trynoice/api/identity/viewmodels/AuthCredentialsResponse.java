package com.trynoice.api.identity.viewmodels;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data transfer object to send auth credentials back to the clients via HTTP responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredentialsResponse {

    @NonNull
    private String refreshToken;

    @NonNull
    private String accessToken;
}
