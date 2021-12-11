package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.models.CreateSubscriptionParams;
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
        when(mockSession.getUrl()).thenReturn(sessionUrl);
        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any())).thenReturn(mockSession);

        mockMvc.perform(
                post("/v1/subscriptions")
                    .header("Authorization", "bearer " + createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateSubscriptionParams(plan.getId(), "/success-callback", "/cancel-callback"))))
            .andExpect(status().is(expectedResponseStatus))
            .andExpect(
                expectedResponseStatus == HttpStatus.CREATED.value()
                    ? header().string("Location", sessionUrl)
                    : header().doesNotExist("Location"));
    }

    static Stream<Arguments> createSubscriptionTestCases() {
        return Stream.of(
            // provider, existing subscription status, response status
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.INACTIVE, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.PENDING, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.ACTIVE, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.ACTIVE, HttpStatus.UNPROCESSABLE_ENTITY.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - expectedSubscriptionStatus={1}")
    @MethodSource("handleGooglePlayWebhookEventTestCases")
    void handleGooglePlayWebhookEvent(
        @NonNull SubscriptionPurchase purchase,
        @NonNull Subscription.Status expectedStatus,
        boolean shouldAcknowledgePurchase
    ) throws Exception {
        val subscriptionId = "test-subscription-id";
        val purchaseToken = "test-purchase-token";
        val data = Base64.getEncoder().encodeToString(("{" +
            "  \"version\": \"1.0\"," +
            "  \"packageName\": \"com.github.ashutoshgngwr.noice\"," +
            "  \"eventTimeMillis\": \"" + System.nanoTime() + "\"," +
            "  \"subscriptionNotification\": {" +
            "    \"version\": \"1.0\"," +
            "    \"notificationType\": 4," +
            "    \"purchaseToken\": \"" + purchaseToken + "\"," +
            "    \"subscriptionId\":\"" + subscriptionId + "\"" +
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
        purchase.setObfuscatedExternalAccountId(authUser.getId().toString());
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, subscriptionId);
        if (purchase.getLinkedPurchaseToken() != null) {
            subscriptionRepository.save(
                Subscription.builder()
                    .plan(plan)
                    .providerSubscriptionId(purchase.getLinkedPurchaseToken())
                    .owner(authUser)
                    .startAt(LocalDateTime.now())
                    .status(Subscription.Status.ACTIVE)
                    .build());
        }

        when(androidPublisherApi.getSubscriptionPurchase(any(), eq(subscriptionId), eq(purchaseToken)))
            .thenReturn(purchase);

        mockMvc.perform(post("/v1/subscriptions/googlePlay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().is(HttpStatus.OK.value()));

        val subscription = subscriptionRepository.findActiveByProviderSubscriptionId(purchaseToken).orElseThrow();
        assertEquals(authUser.getId(), subscription.getOwner().getId());
        assertEquals(expectedStatus, subscription.getStatus());

        verify(androidPublisherApi, times(shouldAcknowledgePurchase ? 1 : 0))
            .acknowledgePurchase(any(), eq(subscriptionId), eq(purchaseToken));

        if (purchase.getLinkedPurchaseToken() != null) {
            val linked = subscriptionRepository.findActiveByProviderSubscriptionId(purchase.getLinkedPurchaseToken()).orElseThrow();
            assertEquals(Subscription.Status.INACTIVE, linked.getStatus());
        }
    }

    static Stream<Arguments> handleGooglePlayWebhookEventTestCases() {
        val futureMillis = System.currentTimeMillis() + 60 * 60 * 1000L;
        val pastMillis = System.currentTimeMillis() - 60 * 60 * 1000L;
        return Stream.of(
            // purchase, expected status, should acknowledge
            arguments(buildSubscriptionPurchase(futureMillis, 1, 1, null), Subscription.Status.ACTIVE, false),
            arguments(buildSubscriptionPurchase(futureMillis, 1, 0, UUID.randomUUID().toString()), Subscription.Status.ACTIVE, true),
            arguments(buildSubscriptionPurchase(futureMillis, 0, 0, null), Subscription.Status.PENDING, true),
            arguments(buildSubscriptionPurchase(pastMillis, 0, 1, null), Subscription.Status.INACTIVE, false),
            arguments(buildSubscriptionPurchase(pastMillis, 1, 0, null), Subscription.Status.INACTIVE, true)
        );
    }

    @NonNull
    private static SubscriptionPurchase buildSubscriptionPurchase(
        long expiryTimeMillis,
        int paymentState,
        int acknowledgementState,
        String linkedPurchaseToken
    ) {
        val purchase = new SubscriptionPurchase();
        purchase.setStartTimeMillis(System.currentTimeMillis());
        purchase.setExpiryTimeMillis(expiryTimeMillis);
        purchase.setPaymentState(paymentState);
        purchase.setAcknowledgementState(acknowledgementState);
        purchase.setLinkedPurchaseToken(linkedPurchaseToken);
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
                .providerSubscriptionId("provider-subscription-id")
                .status(status)
                .startAt(LocalDateTime.now())
                .build());
    }
}
