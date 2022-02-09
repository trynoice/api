package com.trynoice.api.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuration properties used by various auth components in the identity package.
 */
@Validated
@ConfigurationProperties("app.auth")
@Data
@Configuration
class AuthConfiguration {

    static final String REVOKED_ACCESS_JWT_CACHE = "revokedAccessJwts";

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

    @NonNull
    @Bean(REVOKED_ACCESS_JWT_CACHE)
    public Cache<String, Boolean> revokedAccessJwtCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(accessTokenExpiry)
            .maximumSize(10_000) // an arbitrary upper-limit for sanity.
            .build();
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

        @SuppressWarnings("unused")
        @SneakyThrows
        public void setTemplate(String templateFile) {
            val file = new ClassPathResource(templateFile);
            this.template = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        }
    }
}
