package com.trynoice.api.sound;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryManifestRepositoryTest {

    @Mock
    private S3Client mockS3Client;

    @Mock
    private SoundConfiguration soundConfiguration;

    private LibraryManifestRepository repository;

    @BeforeEach
    void setUp() {
        lenient().when(soundConfiguration.getLibraryManifestS3Bucket()).thenReturn("test-s3-bucket");
        lenient().when(soundConfiguration.getLibraryManifestS3Key()).thenReturn("test-manifest-key");

        repository = new LibraryManifestRepository(mockS3Client, new ObjectMapper(), soundConfiguration);
    }

    @Test
    void getPremiumSegmentMappings() {
        val soundId = "test";
        val freeSegmentId = "test_free";
        val premiumSegmentId = "test_premium";
        val testManifestJson = "{" +
            "  \"segmentsBasePath\": \"test-segments\"," +
            "  \"iconsBasePath\": \"test-icons\"," +
            "  \"groups\": [" +
            "    {" +
            "      \"id\": \"test\"," +
            "      \"name\": \"Test\"" +
            "    }" +
            "  ]," +
            "  \"sounds\": [" +
            "    {" +
            "      \"id\": \"" + soundId + "\"," +
            "      \"groupId\": \"test\"," +
            "      \"name\": \"Test\"," +
            "      \"icon\": \"test.svg\"," +
            "      \"maxSilence\": 0," +
            "      \"segments\": [" +
            "        {" +
            "          \"name\": \"" + freeSegmentId + "\"," +
            "          \"isFree\": true" +
            "        }," +
            "        {" +
            "          \"name\": \"" + premiumSegmentId + "\"," +
            "          \"isFree\": false" +
            "        }" +
            "      ]" +
            "    }" +
            "  ]" +
            "}";

        when(mockS3Client.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(testManifestJson.getBytes(StandardCharsets.UTF_8)))));

        val mappings = repository.getPremiumSegmentMappings();
        assertTrue(mappings.containsKey(soundId));
        assertTrue(mappings.get(soundId).contains(premiumSegmentId));
        assertFalse(mappings.get(soundId).contains(freeSegmentId));
    }
}
