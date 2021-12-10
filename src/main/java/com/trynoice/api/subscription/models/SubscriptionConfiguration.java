package com.trynoice.api.subscription.models;

import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

import javax.validation.constraints.NotBlank;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Configuration properties used by various components in the subscription package.
 */
@Data
public class SubscriptionConfiguration {

    @NotBlank
    private String androidApplicationId;

    @NotBlank
    @Getter(AccessLevel.NONE)
    private String androidPublisherApiKeyPath;

    @NonNull
    public GoogleCredentials getAndroidPublisherApiCredentials() throws IOException {
        return GoogleCredentials.fromStream(new FileInputStream(androidPublisherApiKeyPath))
            .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
    }
}
