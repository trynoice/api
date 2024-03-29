package com.trynoice.api.sound;

import com.trynoice.api.subscription.SubscriptionTestUtils;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.testing.AuthTestUtils;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SoundControllerTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @MockBean
    private S3Client mockS3Client;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @MethodSource("authorizeSegmentRequestTestCases")
    void authorizeSegmentRequest(
        boolean isSignedIn,
        Boolean isSubscribed,
        Boolean isPaymentPending,
        int expectedResponseStatus
    ) throws Exception {
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

        val libraryVersion = "test-version";
        val expectedKey = String.format("%s/library-manifest.json", libraryVersion);
        lenient().when(mockS3Client.getObject(argThat((GetObjectRequest r) -> r.key().equals(expectedKey))))
            .thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(testManifestJson.getBytes(StandardCharsets.UTF_8)))));

        val authUser = createAuthUser(entityManager);
        if (isSubscribed != null) {
            val plan = SubscriptionTestUtils.buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "test-id");
            SubscriptionTestUtils.buildSubscription(entityManager, authUser, plan, isSubscribed, isPaymentPending, "test-id");
        }

        val requestUrlFmt = "/v1/sounds/{soundId}/segments/{segmentId}/authorize?audioBitrate={bitrate}&libraryVersion={libraryVersion}";
        val freeSegmentRequest = get(requestUrlFmt, soundId, freeSegmentId, "128k", libraryVersion);
        val premiumSegmentRequest = get(requestUrlFmt, soundId, premiumSegmentId, "128k", libraryVersion);
        val premiumBitrateRequest = get(requestUrlFmt, soundId, freeSegmentId, "320k", libraryVersion);
        if (isSignedIn) {
            val accessToken = createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID);
            freeSegmentRequest.header("Authorization", "Bearer " + accessToken);
            premiumSegmentRequest.header("Authorization", "Bearer " + accessToken);
            premiumBitrateRequest.header("Authorization", "Bearer " + accessToken);
        }

        mockMvc.perform(freeSegmentRequest).andExpect(status().is(HttpStatus.NO_CONTENT.value()));
        mockMvc.perform(premiumSegmentRequest).andExpect(status().is(expectedResponseStatus));
        mockMvc.perform(premiumBitrateRequest).andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> authorizeSegmentRequestTestCases() {
        return Stream.of(
            // isSignedIn, isSubscribed, isPaymentPending, expectedResponseCode
            arguments(false, null, null, HttpStatus.UNAUTHORIZED.value()),
            arguments(true, false, false, HttpStatus.FORBIDDEN.value()),
            arguments(true, true, true, HttpStatus.NO_CONTENT.value()),
            arguments(true, true, false, HttpStatus.NO_CONTENT.value())
        );
    }
}
