package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SubscriptionControllerTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private AndroidPublisherApi androidPublisherApi;

    @MockBean
    private StripeApi stripeApi;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{displayName} - provider={0} responseStatus={1}")
    @MethodSource("getPlansTestCases")
    void getPlans(String provider, int expectedResponseStatus) throws Exception {
        val request = get("/v1/subscriptions/plans");
        if (provider != null) {
            request.queryParam("provider", provider);
        }

        val result = mockMvc.perform(request)
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val plans = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                SubscriptionPlanView[].class);

            assertNotEquals(0, plans.length);
            if (provider != null) {
                for (val plan : plans) {
                    assertEquals(provider, plan.getProvider());
                }
            }
        }
    }

    static Stream<Arguments> getPlansTestCases() {
        return Stream.of(
            arguments(null, HttpStatus.OK.value()),
            arguments("GOOGLE_PLAY", HttpStatus.OK.value()),
            arguments("STRIPE", HttpStatus.OK.value()),
            arguments("UNSUPPORTED_PROVIDER", HttpStatus.UNPROCESSABLE_ENTITY.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - provider={0} existingSubscriptionStatus={1} expectedResponseStatus={2}")
    @MethodSource("createSubscriptionTestCases")
    void createSubscription(
        @NonNull SubscriptionPlan.Provider provider,
        @NonNull Subscription.Status existingSubscriptionStatus,
        int expectedResponseStatus
    ) throws Exception {
        val providerPlanId = "provider-plan-id";
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(provider, providerPlanId);
        buildSubscription(authUser, plan, existingSubscriptionStatus);

        val mockSession = mock(Session.class);
        val sessionUrl = "/checkout-session-url";
        if (provider == SubscriptionPlan.Provider.STRIPE) {
            when(mockSession.getUrl()).thenReturn(sessionUrl);
            when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any())).thenReturn(mockSession);
        }

        val resultActions = mockMvc.perform(
                post("/v1/subscriptions")
                    .header("Authorization", "bearer " + createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new SubscriptionFlowParams(plan.getId(), "https://api.test/success", "https://api.test/cancel"))))
            .andExpect(status().is(expectedResponseStatus));

        if (expectedResponseStatus == HttpStatus.CREATED.value()) {
            resultActions.andExpect(header().exists("Location"))
                .andExpect(header().exists(SubscriptionController.SUBSCRIPTION_ID_HEADER));

            if (provider == SubscriptionPlan.Provider.STRIPE) {
                resultActions.andExpect(
                    header().string(SubscriptionController.STRIPE_CHECKOUT_SESSION_URL_HEADER, sessionUrl));
            }
        } else {
            resultActions.andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist(SubscriptionController.SUBSCRIPTION_ID_HEADER))
                .andExpect(header().doesNotExist(SubscriptionController.STRIPE_CHECKOUT_SESSION_URL_HEADER));
        }
    }

    static Stream<Arguments> createSubscriptionTestCases() {
        return Stream.of(
            // provider, existing subscription status, response status
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.INACTIVE, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.INACTIVE, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.CREATED, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.CREATED, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.PENDING, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.PENDING, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.ACTIVE, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.ACTIVE, HttpStatus.CONFLICT.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - expectedSubscriptionStatus={1}")
    @MethodSource("handleGooglePlayWebhookEventTestCases")
    void handleGooglePlayWebhookEvent(
        @NonNull SubscriptionPurchase purchase,
        @NonNull Subscription.Status existingStatus,
        @NonNull Subscription.Status expectedStatus,
        boolean shouldAcknowledgePurchase
    ) throws Exception {
        val subscriptionPlanId = "test-subscription-id";
        val purchaseToken = UUID.randomUUID().toString();
        val data = Base64.getEncoder().encodeToString(("{" +
            "  \"version\": \"1.0\"," +
            "  \"packageName\": \"com.github.ashutoshgngwr.noice\"," +
            "  \"eventTimeMillis\": \"" + System.nanoTime() + "\"," +
            "  \"subscriptionNotification\": {" +
            "    \"version\": \"1.0\"," +
            "    \"notificationType\": 4," +
            "    \"purchaseToken\": \"" + purchaseToken + "\"," +
            "    \"subscriptionId\":\"" + subscriptionPlanId + "\"" +
            "  }" +
            "}").getBytes(StandardCharsets.UTF_8));

        val eventPayload = "{" +
            "  \"message\": {" +
            "    \"attributes\": {}," +
            "    \"data\": \"" + data + "\"," +
            "    \"messageId\": \"" + System.nanoTime() + "\"" +
            "  }," +
            "  \"subscription\": \"projects/api-7562151365746880729-728328/subscriptions/noice-subscription-events-sub\"" +
            "}";

        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, subscriptionPlanId);
        var subscription = buildSubscription(authUser, plan, existingStatus);
        purchase.setObfuscatedExternalProfileId(subscription.getId().toString());

        var linkedSubscription = buildSubscription(authUser, plan, Subscription.Status.ACTIVE);
        linkedSubscription.setProviderSubscriptionId(UUID.randomUUID().toString());
        subscriptionRepository.save(linkedSubscription);
        purchase.setLinkedPurchaseToken(linkedSubscription.getProviderSubscriptionId());

        when(androidPublisherApi.getSubscriptionPurchase(any(), eq(subscriptionPlanId), eq(purchaseToken)))
            .thenReturn(purchase);

        mockMvc.perform(post("/v1/subscriptions/googlePlay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().is(HttpStatus.OK.value()));

        subscription = subscriptionRepository.findActiveById(subscription.getId()).orElseThrow();
        assertEquals(expectedStatus, subscription.getStatus());
        assertEquals(purchaseToken, subscription.getProviderSubscriptionId());

        verify(androidPublisherApi, times(shouldAcknowledgePurchase ? 1 : 0))
            .acknowledgePurchase(any(), eq(subscriptionPlanId), eq(purchaseToken));

        if (expectedStatus != Subscription.Status.INACTIVE) {
            linkedSubscription = subscriptionRepository.findActiveById(linkedSubscription.getId()).orElseThrow();
            assertEquals(Subscription.Status.INACTIVE, linkedSubscription.getStatus());
        }
    }

    static Stream<Arguments> handleGooglePlayWebhookEventTestCases() {
        val futureMillis = System.currentTimeMillis() + 60 * 60 * 1000L;
        val pastMillis = System.currentTimeMillis() - 60 * 60 * 1000L;
        return Stream.of(
            // purchase, existing subscription status, expected subscription status, should acknowledge
            arguments(
                buildSubscriptionPurchase(futureMillis, 1, 1),
                Subscription.Status.CREATED,
                Subscription.Status.ACTIVE,
                false),
            arguments(
                buildSubscriptionPurchase(futureMillis, 1, 0),
                Subscription.Status.ACTIVE,
                Subscription.Status.ACTIVE,
                true),
            arguments(
                buildSubscriptionPurchase(futureMillis, 1, 0),
                Subscription.Status.PENDING,
                Subscription.Status.ACTIVE,
                true),
            arguments(
                buildSubscriptionPurchase(futureMillis, 1, 0),
                Subscription.Status.INACTIVE,
                Subscription.Status.ACTIVE,
                true),
            arguments(
                buildSubscriptionPurchase(futureMillis, 0, 0),
                Subscription.Status.INACTIVE,
                Subscription.Status.PENDING,
                true),
            arguments(
                buildSubscriptionPurchase(pastMillis, 0, 1),
                Subscription.Status.INACTIVE,
                Subscription.Status.INACTIVE,
                false),
            arguments(
                buildSubscriptionPurchase(pastMillis, 1, 0),
                Subscription.Status.INACTIVE,
                Subscription.Status.INACTIVE,
                true)
        );
    }

    @NonNull
    private static SubscriptionPurchase buildSubscriptionPurchase(long expiryTimeMillis, int paymentState, int acknowledgementState) {
        val purchase = new SubscriptionPurchase();
        purchase.setStartTimeMillis(System.currentTimeMillis());
        purchase.setExpiryTimeMillis(expiryTimeMillis);
        purchase.setPaymentState(paymentState);
        purchase.setAcknowledgementState(acknowledgementState);
        return purchase;
    }

    @NonNull
    private SubscriptionPlan buildSubscriptionPlan(@NonNull SubscriptionPlan.Provider provider, @NonNull String providerPlanId) {
        return subscriptionPlanRepository.save(
            SubscriptionPlan.builder()
                .provider(provider)
                .providerPlanId(providerPlanId)
                .billingPeriodMonths((short) 1)
                .priceInIndianPaise(10000)
                .build());
    }

    @NonNull
    private Subscription buildSubscription(@NonNull AuthUser owner, @NonNull SubscriptionPlan plan, @NonNull Subscription.Status status) {
        return subscriptionRepository.save(
            Subscription.builder()
                .owner(owner)
                .plan(plan)
                .status(status)
                .startAt(LocalDateTime.now())
                .build());
    }
}
