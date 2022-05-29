package com.trynoice.api.subscription;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.Data;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

/**
 * Configuration properties used by various components in the subscription package.
 */
@Validated
@ConfigurationProperties("app.subscriptions")
@Data
@Configuration
public class SubscriptionConfiguration {

    static final String CACHE_NAME = "subscription_cache";

    @NotBlank
    private String androidApplicationId;

    @NotBlank
    private String androidPublisherApiKeyPath;

    @NotBlank
    private String stripeApiKey;

    @NotBlank
    private String stripeWebhookSecret;

    @NotNull
    private Duration cacheTtl;

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

    @NonNull
    @Bean(name = CACHE_NAME)
    Cache cache() {
        return new CaffeineCache(CACHE_NAME, Caffeine.newBuilder()
            .expireAfterWrite(cacheTtl)
            .initialCapacity(100)
            .maximumSize(1000)
            .recordStats()
            .build());
    }
}
