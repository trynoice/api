package com.trynoice.api.identity;

import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Configuration properties used by various auth components in the identity package.
 */
@Validated
@ConfigurationProperties("app.auth")
@Data
@Configuration
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

    @NonNull
    @Bean
    SignInTokenDispatchStrategy signInTokenDispatchStrategy() {
        switch (signInTokenDispatcherType) {
            case EMAIL:
                assert emailSignInTokenDispatcherConfig != null;
                return new SignInTokenDispatchStrategy.Email(emailSignInTokenDispatcherConfig);
            case CONSOLE:
                return new SignInTokenDispatchStrategy.Console();
            default:
                throw new IllegalArgumentException("unsupported sign-in token dispatch strategy: " + signInTokenDispatcherType);
        }

    }

    /**
     * Prevents Spring Web from automatically adding the auth filter bean to its filter chain
     * because it has to be added to Spring Security's filter chain and not Spring Web's.
     */
    @NonNull
    @Bean
    public FilterRegistrationBean<BearerTokenAuthFilter> bearerTokenAuthFilterRegistration(BearerTokenAuthFilter filter) {
        val registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevents Spring Web from automatically adding the auth filter bean to its filter chain
     * because it has to be added to Spring Security's filter chain and not Spring Web's.
     */
    @NonNull
    @Bean
    public FilterRegistrationBean<CookieAuthFilter> cookieAuthFilterRegistration(CookieAuthFilter filter) {
        val registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    public enum SignInTokenDispatcherType {
        CONSOLE,
        EMAIL
    }

    /**
     * Configuration properties required by the {@link SignInTokenDispatchStrategy.Email}.
     */
    @Validated
    @ConfigurationProperties("app.auth.sign-in-token-dispatcher.email")
    @Data
    @Component
    @ConditionalOnProperty(name = "app.auth.sign-in-token-dispatcher-type", havingValue = "email")
    static class EmailSignInTokenDispatcherConfiguration {

        /**
         * Used as source address when sending sign-in emails.
         */
        @NotBlank
        @Email
        private String fromEmail;

        /**
         * Subject line for the sign-in emails.
         */
        @NotBlank
        private String subject;

        /**
         * Template of the sign-in email body.
         */
        @NotBlank
        private String template;

        /**
         * Template of the sign-in link in the email body.
         */
        @NotBlank
        private String linkFmt;

        /**
         * A support email to include in sign-in email body.
         */
        @NotBlank
        @Email
        private String supportEmail;

        @SuppressWarnings("unused")
        @SneakyThrows
        public void setTemplate(String template) {
            val file = ResourceUtils.getFile(template);
            // remove extra indentation spaces.
            this.template = Files.readString(file.toPath()).replaceAll("\\s+", " ");
        }
    }
}
