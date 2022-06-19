package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.converter.JacksonPubSubMessageConverter;
import com.google.cloud.spring.pubsub.support.converter.PubSubMessageConverter;
import com.trynoice.api.subscription.payload.GooglePlayDeveloperNotification;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Spring Beans used by the subscription package.
 */
@Configuration
class SubscriptionBeans {

    static final String CACHE_NAME = "subscription_cache";
    static final String GOOGLE_PLAY_DEVELOPER_NOTIFICATION_CHANNEL = "googlePlayDeveloperNotificationChannel";

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

    @Bean(name = GOOGLE_PLAY_DEVELOPER_NOTIFICATION_CHANNEL)
    public MessageChannel googlePlayDeveloperNotificationChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean
    @Primary
    public PubSubMessageConverter pubSubMessageConverter(@NonNull ObjectMapper objectMapper) {
        return new JacksonPubSubMessageConverter(objectMapper);
    }

    @Bean
    @Profile("!test")
    public PubSubInboundChannelAdapter gcpPubSubInboundChannelAdapter(
        @NonNull SubscriptionConfiguration config,
        @NonNull PubSubTemplate pubSubTemplate,
        @NonNull @Qualifier(GOOGLE_PLAY_DEVELOPER_NOTIFICATION_CHANNEL) MessageChannel channel
    ) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate, config.getGcpPubsubSubName());
        adapter.setOutputChannel(channel);
        adapter.setAckMode(AckMode.AUTO);
        adapter.setPayloadType(GooglePlayDeveloperNotification.class);
        return adapter;
    }
}
