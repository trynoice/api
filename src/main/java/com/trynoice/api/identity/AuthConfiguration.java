package com.trynoice.api.identity;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Configuration properties used by various auth components in the identity package.
 */
@Validated
@ConfigurationProperties("app.auth")
@ConstructorBinding
@Data
class AuthConfiguration {

    /**
     * HMAC secret to sign refresh and access tokens.
     */
    @NotBlank
    private final String hmacSecret;

    /**
     * Expiry duration for refresh tokens.
     */
    @NotNull
    private final Duration refreshTokenExpiry;

    /**
     * Expiry duration for access tokens.
     */
    @NotNull
    private final Duration accessTokenExpiry;

    /**
     * Expiry duration for sign-in tokens.
     */
    @NotNull
    private final Duration signInTokenExpiry;

    /**
     * Maximum timeout duration for an account on making too many incomplete sign-in attempts.
     */
    @NotNull
    private final Duration signInReattemptMaxDelay;

    /**
     * Root domain for auth cookies.
     */
    @NotBlank
    private final String cookieDomain;

    @NotNull
    private final Duration removeDeactivatedAccountsAfter;

    /**
     * {@link SignInTokenDispatchStrategy} to use for dispatching sign-in tokens. It should always
     * be set to {@link SignInTokenDispatcherType#EMAIL} in production environment.
     */
    @NotNull
    private final SignInTokenDispatcherType signInTokenDispatcherType;

    @Autowired(required = false)
    private transient final EmailSignInTokenDispatcherConfiguration emailSignInTokenDispatcherConfig;

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
    @ConstructorBinding
    @Data
    static class EmailSignInTokenDispatcherConfiguration {

        /**
         * Used as source address when sending sign-in emails.
         */
        @NotBlank
        private final String from;

        /**
         * Subject line for the sign-in emails.
         */
        @NotBlank
        private final String subject;
    }
}
