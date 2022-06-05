package com.trynoice.api.identity;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Beans used by various auth components in the identity package.
 */
@Configuration
class AuthBeans {

    static final String REVOKED_ACCESS_JWT_CACHE = "revoked_access_jwts";
    static final String DELETED_USER_ID_CACHE = "deleted_user_ids";

    @NonNull
    @Bean
    SignInTokenDispatchStrategy signInTokenDispatchStrategy(
        @NonNull AuthConfiguration authConfig,
        @Autowired(required = false) AuthConfiguration.EmailSignInTokenDispatcherConfiguration emailDispatcherConfig
    ) {
        switch (authConfig.getSignInTokenDispatcherType()) {
            case EMAIL:
                assert emailDispatcherConfig != null;
                return new SignInTokenDispatchStrategy.Email(emailDispatcherConfig);
            case CONSOLE:
                return new SignInTokenDispatchStrategy.Console();
            default:
                throw new IllegalArgumentException("unsupported sign-in token dispatch strategy: " + authConfig.getSignInTokenDispatcherType());
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
    public Cache<String, Boolean> revokedAccessJwtCache(@NonNull AuthConfiguration authConfig) {
        return Caffeine.newBuilder()
            .expireAfterWrite(authConfig.getAccessTokenExpiry())
            .maximumSize(1000) // an arbitrary upper-limit for sanity.
            .build();
    }

    @NonNull
    @Bean(DELETED_USER_ID_CACHE)
    public Cache<Long, Boolean> deleteUserIdCache(@NonNull AuthConfiguration authConfig) {
        return Caffeine.newBuilder()
            .expireAfterWrite(authConfig.getAccessTokenExpiry())
            .maximumSize(1000) // an arbitrary upper-limit for sanity.
            .build();
    }
}
