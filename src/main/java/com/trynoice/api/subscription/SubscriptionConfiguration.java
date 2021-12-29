package com.trynoice.api.subscription;

import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.Data;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Configuration properties used by various components in the subscription package.
 */
@Validated
@ConfigurationProperties("app.subscriptions")
@Data
@Configuration
public class SubscriptionConfiguration {

    @NotBlank
    private String androidApplicationId;

    @NotBlank
    private String androidPublisherApiKeyPath;

    @NotBlank
    private String stripeApiKey;

    @NotBlank
    private String stripeWebhookSecret;

    @NonNull
    GoogleCredentials androidPublisherApiCredentials() throws IOException {
        return GoogleCredentials.fromStream(new FileInputStream(androidPublisherApiKeyPath))
            .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
    }

    @NonNull
    @Bean
    AndroidPublisherApi androidPublisherApi(
        @NonNull Environment environment,
        @NonNull SubscriptionConfiguration config,
        @NonNull @Value("${spring.application.name}") String appName
    ) throws IOException, GeneralSecurityException {
        GoogleCredentials credentials;
        try {
            credentials = androidPublisherApiCredentials();
        } catch (IOException e) {
            // create dummy credentials when running tests since credentials file may not be available.
            if (environment.acceptsProfiles(Profiles.of("!test"))) {
                throw e;
            }

            credentials = GoogleCredentials.create(new AccessToken("dummy-token", null));
        }

        return new AndroidPublisherApi(credentials, appName);
    }

    @NonNull
    @Bean
    StripeApi stripeApi(@NonNull SubscriptionConfiguration config) {
        return new StripeApi(config.getStripeApiKey());
    }
}
