package com.trynoice.api.identity.models;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * A data transfer object to send auth credentials back to the clients via HTTP responses.
 */
@Data
@Builder
public class AuthCredentials {

    @NonNull
    private final String refreshToken;

    @NonNull
    private final String accessToken;
}
