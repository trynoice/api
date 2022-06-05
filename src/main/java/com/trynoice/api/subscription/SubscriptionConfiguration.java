package com.trynoice.api.subscription;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Configuration properties used by various components in the subscription package.
 */
@Validated
@ConfigurationProperties("app.subscriptions")
@Data
@Component
public class SubscriptionConfiguration {

    @NotBlank
    private String androidApplicationId;

    @NotBlank
    private String googlePlayApiKeyPath;

    @NotNull
    private boolean googlePlayTestModeEnabled;

    @NotBlank
    private String stripeApiKey;

    @NotBlank
    private String stripeWebhookSecret;

    @NotNull
    private Duration cacheTtl;
}
