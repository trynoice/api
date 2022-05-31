package com.trynoice.api.sound;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

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
     * TTL for objects in library manifest cache.
     */
    @NotNull
    private Duration libraryManifestCacheTtl;

    @NotNull
    private Set<String> freeBitrates;

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
    S3Client s3Client(@NonNull Environment environment) {
        // since `S3Client.create()` loads the AWS CLI configuration to obtain default credentials,
        // it throws error in test environments (CI). Therefore, explicitly create a dummy S3 client
        // during tests with minimal configuration.
        return environment.acceptsProfiles(Profiles.of("test"))
            ? S3Client.builder().region(Region.US_WEST_2).build()
            : S3Client.create();
    }
}
