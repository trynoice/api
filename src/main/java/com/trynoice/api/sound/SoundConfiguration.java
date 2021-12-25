package com.trynoice.api.sound;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.s3.S3Client;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Configuration properties used by various components in the sound package.
 */
@Validated
@ConfigurationProperties("app.sounds")
@Data
@Configuration
public class SoundConfiguration {

    /**
     * S3 bucket that hosts the library manifest JSON.
     */
    @NotBlank
    private String libraryManifestS3Bucket;

    /**
     * Key for fetching the library manifest from the given S3 bucket.
     */
    @NotBlank
    private String libraryManifestS3Key;

    /**
     * TTL for objects in library manifest cache.
     */
    @NotNull
    private Duration libraryManifestCacheTtl;

    @NonNull
    @Bean
    Cache libraryManifestCache() {
        return new CaffeineCache(LibraryManifestRepository.CACHE, Caffeine.newBuilder()
            .expireAfterWrite(libraryManifestCacheTtl)
            .initialCapacity(2)
            .maximumSize(100) // a leeway window for future usage.
            .recordStats()
            .build());
    }

    @NonNull
    @Bean
    S3Client s3Client() {
        return S3Client.create();
    }
}
