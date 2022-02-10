package com.trynoice.api.sound;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.testing.AuthTestUtils;
import lombok.NonNull;
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

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
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

    @ParameterizedTest(name = "{displayName} - isSignedIn={0} subscriptionStatus={1} responseStatus={2}")
    @MethodSource("authorizeSegmentRequestTestCases")
    void authorizeSegmentRequest(boolean isSignedIn, Subscription.Status subscriptionStatus, int expectedResponseStatus) throws Exception {
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

        lenient().when(mockS3Client.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(
                    new ByteArrayInputStream(testManifestJson.getBytes(StandardCharsets.UTF_8)))));

        val authUser = createAuthUser(entityManager);
        if (subscriptionStatus != null) {
            buildSubscription(authUser, subscriptionStatus);
        }

        val requestUrlFmt = "/v1/sounds/%s/segments/%s/authorize?audioBitrate=%s";
        val freeSegmentRequest = get(String.format(requestUrlFmt, soundId, freeSegmentId, "32k"));
        val premiumSegmentRequest = get(String.format(requestUrlFmt, soundId, premiumSegmentId, "32k"));
        val premiumBitrateRequest = get(String.format(requestUrlFmt, soundId, freeSegmentId, "320k"));
        if (isSignedIn) {
            val accessToken = createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID);
            freeSegmentRequest.header("Authorization", "Bearer " + accessToken);
            premiumSegmentRequest.header("Authorization", "Bearer " + accessToken);
            premiumBitrateRequest.header("Authorization", "Bearer " + accessToken);
        }

        mockMvc.perform(freeSegmentRequest).andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(premiumSegmentRequest).andExpect(status().is(expectedResponseStatus));
        mockMvc.perform(premiumBitrateRequest).andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> authorizeSegmentRequestTestCases() {
        return Stream.of(
            // isSignedIn, subscriptionStatus, expectedResponseCode
            arguments(false, null, HttpStatus.UNAUTHORIZED.value()),
            arguments(true, Subscription.Status.CREATED, HttpStatus.FORBIDDEN.value()),
            arguments(true, Subscription.Status.INACTIVE, HttpStatus.FORBIDDEN.value()),
            arguments(true, Subscription.Status.PENDING, HttpStatus.OK.value()),
            arguments(true, Subscription.Status.ACTIVE, HttpStatus.OK.value())
        );
    }

    @NonNull
    private Subscription buildSubscription(@NonNull AuthUser owner, @NonNull Subscription.Status status) {
        val subscription = Subscription.builder()
            .ownerId(owner.getId())
            .plan(buildSubscriptionPlan())
            .providerSubscriptionId(UUID.randomUUID().toString())
            .status(status)
            .startAt(LocalDateTime.now())
            .build();

        entityManager.persist(subscription);
        return subscription;
    }

    @NonNull
    private SubscriptionPlan buildSubscriptionPlan() {
        val plan = SubscriptionPlan.builder()
            .provider(SubscriptionPlan.Provider.STRIPE)
            .providerPlanId(UUID.randomUUID().toString().substring(0, 16))
            .billingPeriodMonths((short) 1)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(10000)
            .build();

        entityManager.persist(plan);
        return plan;
    }
}
