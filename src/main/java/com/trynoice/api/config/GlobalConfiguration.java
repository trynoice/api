package com.trynoice.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Global application configuration properties.
 */
@Validated
@ConfigurationProperties("app")
@ConstructorBinding
@Data
public class GlobalConfiguration {

    @NotNull
    private final Duration removeDeletedEntitiesAfter;
}
