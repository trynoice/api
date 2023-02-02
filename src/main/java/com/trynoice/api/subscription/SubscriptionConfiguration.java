package com.trynoice.api.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties used by various components in the subscription package.
 */
@Validated
@ConfigurationProperties("app.subscriptions")
@Data
class SubscriptionConfiguration {

    @NotBlank
    private final String androidApplicationId;

    @NotBlank
    private final String googlePlayApiKeyPath;

    @NotBlank
    private final String gcpPubsubSubName;

    @NotNull
    private final boolean googlePlayTestModeEnabled;

    @NotBlank
    private final String stripeApiKey;

    @NotBlank
    private final String stripeWebhookSecret;

    @NotNull
    private final Duration stripeCheckoutSessionExpiry;

    @NotNull
    private final Duration cacheTtl;

    @NotNull
    private final Duration removeIncompleteSubscriptionsAfter;
}
