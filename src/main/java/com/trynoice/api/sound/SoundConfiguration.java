package com.trynoice.api.sound;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Configuration properties used by various components in the sound package.
 */
@Validated
@ConfigurationProperties("app.sounds")
@Data
public class SoundConfiguration {

    /**
     * S3 bucket prefix (excluding the library version) that hosts the sound library.
     */
    @NotNull
    private final URI libraryS3Prefix;

    /**
     * TTL for objects in library manifest cache.
     */
    @NotNull
    private final Duration libraryCacheTtl;

    @NotNull
    private final Set<String> freeBitrates;
}
