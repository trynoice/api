package com.trynoice.api.subscription;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Spring Beans used by the subscription package.
 */
@Configuration
class SubscriptionBeans {

    static final String CACHE_NAME = "subscription_cache";

    @NonNull
    GoogleCredentials googlePlayApiCredentials(@NonNull SubscriptionConfiguration config) throws IOException {
        return GoogleCredentials.fromStream(new FileInputStream(config.getGooglePlayApiKeyPath()))
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
            credentials = googlePlayApiCredentials(config);
        } catch (IOException e) {
            // create dummy credentials when running tests since credentials file may not be available.
            if (environment.acceptsProfiles(Profiles.of("!test"))) {
                throw e;
            }

            credentials = GoogleCredentials.create(new AccessToken("dummy-token", null));
        }

        return new AndroidPublisherApi(credentials, appName, config.getAndroidApplicationId());
    }

    @NonNull
    @Bean
    StripeApi stripeApi(@NonNull SubscriptionConfiguration config) {
        return new StripeApi(config.getStripeApiKey());
    }

    @NonNull
    @Bean(name = CACHE_NAME)
    Cache cache(@NonNull SubscriptionConfiguration config) {
        return new CaffeineCache(CACHE_NAME, Caffeine.newBuilder()
            .expireAfterWrite(config.getCacheTtl())
            .initialCapacity(100)
            .maximumSize(1000)
            .recordStats()
            .build());
    }
}
