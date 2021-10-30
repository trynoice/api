package com.trynoice.api.identity.models;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Configuration properties used by various auth components in the identity package.
 */
@Data
public class AuthConfiguration {

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
     * {@link com.trynoice.api.identity.SignInTokenDispatchStrategy SignInTokenDispatchStrategy} to
     * use for dispatching sign-in tokens. It should always be set to {@link
     * SignInTokenDispatcherType#EMAIL} in production environment.
     */
    @NotNull
    private SignInTokenDispatcherType signInTokenDispatcherType;

    public enum SignInTokenDispatcherType {
        CONSOLE,
        EMAIL
    }
}
