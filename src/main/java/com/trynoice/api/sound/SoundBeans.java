package com.trynoice.api.sound;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Spring Beans used by the sound package.
 */
@Configuration
public class SoundBeans {

    public static final String CACHE_NAME = "sound_cache";

    @NonNull
    @Bean(name = CACHE_NAME)
    Cache cache(@NonNull SoundConfiguration config) {
        return new CaffeineCache(CACHE_NAME, Caffeine.newBuilder()
            .expireAfterWrite(config.getLibraryCacheTtl())
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
