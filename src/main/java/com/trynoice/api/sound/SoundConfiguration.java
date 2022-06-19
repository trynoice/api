package com.trynoice.api.sound;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Configuration properties used by various components in the sound package.
 */
@Validated
@ConfigurationProperties("app.sounds")
@ConstructorBinding
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
