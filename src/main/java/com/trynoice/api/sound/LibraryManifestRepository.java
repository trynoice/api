package com.trynoice.api.sound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.sound.entities.LibraryManifest;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A repository implementation to fetch the library manifest from the S3 bucket.
 */
@Repository
public class LibraryManifestRepository {

    static final String CACHE = "library_manifest_cache";

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final SoundConfiguration soundConfig;

    @Autowired
    LibraryManifestRepository(@NonNull S3Client s3Client, @NonNull ObjectMapper objectMapper, @NonNull SoundConfiguration soundConfig) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.soundConfig = soundConfig;
    }

    /**
     * Fetches and deserialises the {@link LibraryManifest} from the S3 bucket and then generates a
     * map of premium segments in all the sounds. The result is cached for a defined period of time.
     * The TTL of this cache can be configured using the `app.sounds.library-manifest-cache-ttl`.
     *
     * @return a map of sound ids to a set of premium segment ids.
     */
    @NonNull
    @Cacheable(CACHE)
    public Map<String, Set<String>> getPremiumSegmentMappings() {
        return get().getSounds()
            .stream()
            .collect(Collectors.toMap(LibraryManifest.Sound::getId, sound ->
                sound.getSegments()
                    .stream()
                    .filter(segment -> !Boolean.TRUE.equals(segment.getIsFree()))
                    .map(LibraryManifest.Segment::getName)
                    .collect(Collectors.toUnmodifiableSet())));
    }

    @NonNull
    private LibraryManifest get() {
        val result = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(soundConfig.getLibraryManifestS3Bucket())
                .key(soundConfig.getLibraryManifestS3Key())
                .build());

        try {
            return objectMapper.readValue(result, LibraryManifest.class);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse library manifest", e);
        }
    }
}
