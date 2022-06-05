package com.trynoice.api.identity;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Configuration properties used by various auth components in the identity package.
 */
@Validated
@ConfigurationProperties("app.auth")
@Data
@Component
class AuthConfiguration {

    /**
     * HMAC secret to sign refresh and access tokens.
     */
    @NotBlank
    private String hmacSecret;

    /**
     * Expiry duration for refresh tokens.
     */
    @NotNull
    private Duration refreshTokenExpiry;

    /**
     * Expiry duration for access tokens.
     */
    @NotNull
    private Duration accessTokenExpiry;

    /**
     * Expiry duration for sign-in tokens.
     */
    @NotNull
    private Duration signInTokenExpiry;

    /**
     * Maximum timeout duration for an account on making too many incomplete sign-in attempts.
     */
    @NotNull
    private Duration signInReattemptMaxDelay;

    /**
     * Root domain for auth cookies.
     */
    @NotBlank
    private String cookieDomain;

    /**
     * {@link SignInTokenDispatchStrategy} to use for dispatching sign-in tokens. It should always
     * be set to {@link SignInTokenDispatcherType#EMAIL} in production environment.
     */
    @NotNull
    private SignInTokenDispatcherType signInTokenDispatcherType;

    @Autowired(required = false)
    private transient EmailSignInTokenDispatcherConfiguration emailSignInTokenDispatcherConfig;

    public enum SignInTokenDispatcherType {
        CONSOLE,
        EMAIL
    }

    /**
     * Configuration properties required by the {@link SignInTokenDispatchStrategy.Email}.
     */
    @Validated
    @ConfigurationProperties("app.auth.sign-in-token-dispatcher.email")
    @ConditionalOnProperty(name = "app.auth.sign-in-token-dispatcher-type", havingValue = "email")
    @Data
    @Component
    static class EmailSignInTokenDispatcherConfiguration {

        /**
         * Used as source address when sending sign-in emails.
         */
        @NotBlank
        private String from;

        /**
         * Subject line for the sign-in emails.
         */
        @NotBlank
        private String subject;
    }
}
