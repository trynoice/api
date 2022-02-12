package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.testing.AuthTestUtils;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.Test;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private CustomerRepository customerRepository;

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
                assertTrue(
                    Arrays.stream(plans)
                        .allMatch(p -> provider.equalsIgnoreCase(p.getProvider())));
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
        val successUrl = "https://api.test/success";
        val cancelUrl = "https://api.test/cancel";
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(provider, providerPlanId);
        buildSubscription(authUser, plan, existingSubscriptionStatus);

        val mockSession = mock(Session.class);
        val sessionUrl = "/checkout-session-url";
        if (provider == SubscriptionPlan.Provider.STRIPE) {
            val customer = buildCustomer(authUser);
            when(mockSession.getUrl()).thenReturn(sessionUrl);
            when(
                stripeApi.createCheckoutSession(
                    eq(successUrl),
                    eq(cancelUrl),
                    eq(plan.getProviderPlanId()),
                    any(),
                    eq(authUser.getEmail()),
                    eq(customer.getStripeId()),
                    any()))
                .thenReturn(mockSession);
        }

        val resultActions = mockMvc.perform(
                post("/v1/subscriptions")
                    .header("Authorization", "bearer " + createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new SubscriptionFlowParams(plan.getId(), successUrl, cancelUrl))))
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

    @Test
    void getSubscription() throws Exception {
        val subscriptionPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val random = new Random();
        val statuses = Subscription.Status.values();
        val data = new HashMap<AuthUser, List<Subscription>>();
        for (int i = 0; i < 5; i++) {
            val authUser = createAuthUser(entityManager);
            val subscriptions = new ArrayList<Subscription>(5);
            for (int j = 0; j < 5; j++) {
                subscriptions.add(
                    buildSubscription(authUser, subscriptionPlan, statuses[random.nextInt(statuses.length)]));
            }

            data.put(authUser, subscriptions);
        }

        for (val entry : data.entrySet()) {
            val testReturnUrl = "https://test-return-url";
            val testCustomerPortalUrl = "test-customer-portal-url";
            val mockSession = mock(com.stripe.model.billingportal.Session.class);
            lenient().when(mockSession.getUrl()).thenReturn(testCustomerPortalUrl);

            val customer = buildCustomer(entry.getKey());
            entry.getValue().stream()
                .filter(s -> s.getPlan().getProvider() == SubscriptionPlan.Provider.STRIPE)
                .forEach(s -> {
                    try {
                        lenient().when(stripeApi.createCustomerPortalSession(customer.getStripeId(), testReturnUrl))
                            .thenReturn(mockSession);
                    } catch (StripeException e) {
                        throw new RuntimeException(e);
                    }
                });

            val accessToken = createSignedAccessJwt(hmacSecret, entry.getKey(), AuthTestUtils.JwtType.VALID);
            val result = mockMvc.perform(
                    get("/v1/subscriptions?stripeReturnUrl=" + testReturnUrl)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andReturn();

            val expectedIds = entry.getValue()
                .stream()
                .filter(s -> s.getStatus() != Subscription.Status.CREATED)
                .map(s -> String.valueOf(s.getId()))
                .sorted()
                .collect(Collectors.toList());

            val actualSubscriptions = objectMapper.readValue(result.getResponse().getContentAsByteArray(), JsonNode[].class);
            val actualIds = Arrays.stream(actualSubscriptions)
                .map(v -> v.findValue("id").asText())
                .sorted()
                .collect(Collectors.toList());

            assertEquals(expectedIds, actualIds);
            for (val actualSubscription : actualSubscriptions) {
                val actualIsActive = actualSubscription.at("/isActive").asBoolean(false);
                val actualProvider = actualSubscription.at("/plan/provider").asText();
                val stripeCustomerPortalUrlNode = actualSubscription.at("/stripeCustomerPortalUrl");
                if (actualIsActive && actualProvider.equalsIgnoreCase(SubscriptionPlan.Provider.STRIPE.name())) {
                    assertEquals(testCustomerPortalUrl, stripeCustomerPortalUrlNode.asText());
                } else {
                    assertTrue(stripeCustomerPortalUrlNode.isMissingNode() || stripeCustomerPortalUrlNode.isNull());
                }
            }
        }
    }

    @ParameterizedTest(name = "{displayName} - provider={0} status={1} expectedResponseStatus={2}")
    @MethodSource("cancelSubscriptionTestCases")
    void cancelSubscription(
        @NonNull SubscriptionPlan.Provider provider,
        @NonNull Subscription.Status status,
        int expectedResponseCode
    ) throws Exception {
        val actualOwner = createAuthUser(entityManager);
        val impersonator = createAuthUser(entityManager);
        val actualOwnerAccessToken = createSignedAccessJwt(hmacSecret, actualOwner, AuthTestUtils.JwtType.VALID);
        val impersonatorAccessToken = createSignedAccessJwt(hmacSecret, impersonator, AuthTestUtils.JwtType.VALID);

        val plan = buildSubscriptionPlan(provider, "provider-plan-id");
        val subscription = buildSubscription(actualOwner, plan, status);

        mockMvc.perform(
                delete("/v1/subscriptions/" + subscription.getId())
                    .header("Authorization", "Bearer " + impersonatorAccessToken))
            .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(
                delete("/v1/subscriptions/" + subscription.getId())
                    .header("Authorization", "Bearer " + actualOwnerAccessToken))
            .andExpect(status().is(expectedResponseCode));

        if (expectedResponseCode == HttpStatus.NO_CONTENT.value()) {
            assertEquals(Subscription.Status.INACTIVE, subscription.getStatus());
            switch (provider) {
                case GOOGLE_PLAY:
                    verify(androidPublisherApi, times(1))
                        .cancelSubscription(
                            any(),
                            eq(plan.getProviderPlanId()),
                            eq(subscription.getProviderSubscriptionId()));
                    break;
                case STRIPE:
                    verify(stripeApi, times(1))
                        .cancelSubscription(eq(subscription.getProviderSubscriptionId()));
                    break;
                default:
                    throw new RuntimeException("unknown provider");
            }
        }
    }

    static Stream<Arguments> cancelSubscriptionTestCases() {
        return Stream.of(
            // subscription provider, subscription status, expected response code
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.ACTIVE, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.ACTIVE, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.PENDING, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.PENDING, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.CREATED, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.CREATED, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, Subscription.Status.INACTIVE, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, Subscription.Status.INACTIVE, HttpStatus.CONFLICT.value())
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
        val subscription = buildSubscription(authUser, plan, existingStatus);
        subscription.setProviderSubscriptionId(null);
        subscriptionRepository.save(subscription);

        purchase.setObfuscatedExternalProfileId(String.valueOf(subscription.getId()));

        val linkedSubscription = buildSubscription(authUser, plan, Subscription.Status.ACTIVE);
        purchase.setLinkedPurchaseToken(linkedSubscription.getProviderSubscriptionId());

        when(androidPublisherApi.getSubscriptionPurchase(any(), eq(subscriptionPlanId), eq(purchaseToken)))
            .thenReturn(purchase);

        mockMvc.perform(post("/v1/subscriptions/googlePlay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertEquals(expectedStatus, subscription.getStatus());
        assertEquals(purchaseToken, subscription.getProviderSubscriptionId());

        verify(androidPublisherApi, times(shouldAcknowledgePurchase ? 1 : 0))
            .acknowledgePurchase(any(), eq(subscriptionPlanId), eq(purchaseToken));

        if (expectedStatus != Subscription.Status.INACTIVE) {
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

    @ParameterizedTest(name = "{displayName} - session.status={0} session.paymentStatus={1} expectedSubscriptionStatus={2}")
    @MethodSource("handleStripeWebhookEvent_checkoutSessionCompleteTestCases")
    void handleStripeWebhookEvent_checkoutSessionComplete(
        @NonNull String sessionStatus,
        @NonNull String sessionPaymentStatus,
        @NonNull Subscription.Status expectedSubscriptionStatus
    ) throws Exception {
        val subscription = buildSubscription(
            createAuthUser(entityManager),
            buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-plan"),
            Subscription.Status.CREATED);

        val checkoutSession = buildStripeCheckoutSession(sessionStatus, sessionPaymentStatus);
        checkoutSession.setClientReferenceId(String.valueOf(subscription.getId()));

        val event = buildStripeEvent("checkout.session.completed", checkoutSession);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        lenient().when(stripeApi.getSubscription(any()))
            .thenReturn(mock(com.stripe.model.Subscription.class));

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertEquals(expectedSubscriptionStatus, subscription.getStatus());
    }

    static Stream<Arguments> handleStripeWebhookEvent_checkoutSessionCompleteTestCases() {
        return Stream.of(
            // session status, payment status, expected subscription status
            arguments("expired", "no_payment_required", Subscription.Status.CREATED),
            arguments("complete", "no_payment_required", Subscription.Status.CREATED),
            arguments("complete", "unpaid", Subscription.Status.CREATED),
            arguments("complete", "paid", Subscription.Status.ACTIVE)
        );
    }

    @ParameterizedTest(name = "{displayName} - stripeStatus={0} expectedInternalStatus={1}")
    @MethodSource("handleStripeWebhookEvent_subscriptionEventsTestCases")
    void handleStripeWebhookEvent_subscriptionEvents(
        @NonNull String stripeSubscriptionStatus,
        @NonNull Subscription.Status expectedInternalSubscriptionStatus
    ) throws Exception {
        val subscriptionPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id");
        val subscription = buildSubscription(createAuthUser(entityManager), subscriptionPlan, Subscription.Status.CREATED);
        val stripeSubscriptionId = UUID.randomUUID().toString();
        subscription.setProviderSubscriptionId(stripeSubscriptionId);
        subscriptionRepository.save(subscription);

        val stripeSubscription = buildStripeSubscription(stripeSubscriptionStatus);
        stripeSubscription.setId(stripeSubscriptionId);
        val stripeSubscriptionItems = new SubscriptionItemCollection();
        stripeSubscriptionItems.setData(List.of(buildSubscriptionItem(subscriptionPlan.getProviderPlanId())));
        stripeSubscription.setItems(stripeSubscriptionItems);

        val event = buildStripeEvent("customer.subscription.updated", stripeSubscription);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertEquals(expectedInternalSubscriptionStatus, subscription.getStatus());
    }

    static Stream<Arguments> handleStripeWebhookEvent_subscriptionEventsTestCases() {
        return Stream.of(
            // stripe subscription status, expected internal subscription status
            arguments("incomplete", Subscription.Status.INACTIVE),
            arguments("incomplete_expired", Subscription.Status.INACTIVE),
            arguments("trialing", Subscription.Status.ACTIVE),
            arguments("active", Subscription.Status.ACTIVE),
            arguments("past_due", Subscription.Status.PENDING),
            arguments("canceled", Subscription.Status.INACTIVE),
            arguments("unpaid", Subscription.Status.INACTIVE)
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
                .trialPeriodDays((short) 1)
                .priceInIndianPaise(10000)
                .build());
    }

    @NonNull
    private Subscription buildSubscription(@NonNull AuthUser owner, @NonNull SubscriptionPlan plan, @NonNull Subscription.Status status) {
        return subscriptionRepository.save(
            Subscription.builder()
                .ownerId(owner.getId())
                .plan(plan)
                .providerSubscriptionId(UUID.randomUUID().toString())
                .status(status)
                .startAt(LocalDateTime.now())
                .build());
    }

    @NonNull
    private Customer buildCustomer(@NonNull AuthUser user) {
        return customerRepository.save(
            Customer.builder()
                .userId(user.getId())
                .stripeId(UUID.randomUUID().toString())
                .build());
    }

    @NonNull
    private static Event buildStripeEvent(@NonNull String type, @NonNull StripeObject dataObject) {
        // TODO: maybe find a fix in free time.
        // mock because event data object serialization/deserialization is confusing and all my
        // attempts failed.
        val event = mock(Event.class);
        lenient().when(event.getType()).thenReturn(type);

        val deserializer = mock(EventDataObjectDeserializer.class);
        lenient().when(deserializer.getObject()).thenReturn(Optional.of(dataObject));
        lenient().when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        lenient().when(event.toJson()).thenReturn("{}");
        return event;
    }

    @NonNull
    private static Session buildStripeCheckoutSession(@NonNull String status, @NonNull String paymentStatus) {
        val session = new Session();
        session.setMode("subscription");
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setSubscription(UUID.randomUUID().toString());
        session.setCustomer(UUID.randomUUID().toString());
        return session;
    }

    @NonNull
    private static com.stripe.model.Subscription buildStripeSubscription(@NonNull String status) {
        val subscription = new com.stripe.model.Subscription();
        subscription.setStatus(status);

        val now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
        subscription.setCurrentPeriodStart(now);
        subscription.setStartDate(now);
        subscription.setCurrentPeriodEnd(now + 60 * 60);
        return subscription;
    }

    @NonNull
    private static SubscriptionItem buildSubscriptionItem(@NonNull String priceId) {
        val price = new Price();
        price.setId(priceId);

        val subscriptionItem = new SubscriptionItem();
        subscriptionItem.setPrice(price);
        return subscriptionItem;
    }
}
