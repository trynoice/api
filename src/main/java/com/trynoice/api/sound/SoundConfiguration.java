package com.trynoice.api.sound;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
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
@Data
@Component
class SoundConfiguration {

    /**
     * S3 bucket prefix (excluding the library version) that hosts the sound library.
     */
    @NotNull
    private URI libraryS3Prefix;

    /**
     * TTL for objects in library manifest cache.
     */
    @NotNull
    private Duration libraryCacheTtl;

    @NotNull
    private Set<String> freeBitrates;
}
