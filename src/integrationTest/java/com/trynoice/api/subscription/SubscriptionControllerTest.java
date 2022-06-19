package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionPlanRepository;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionPlanResult;
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
import org.springframework.test.web.servlet.ResultActions;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @MethodSource("listPlansTestCases")
    void listPlans(String provider, int expectedResponseStatus) throws Exception {
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
                SubscriptionPlanResult[].class);

            assertNotEquals(0, plans.length);
            if (provider != null) {
                assertTrue(
                    Arrays.stream(plans)
                        .allMatch(p -> provider.equalsIgnoreCase(p.getProvider())));
            }
        }
    }

    static Stream<Arguments> listPlansTestCases() {
        return Stream.of(
            arguments(null, HttpStatus.OK.value()),
            arguments("GOOGLE_PLAY", HttpStatus.OK.value()),
            arguments("STRIPE", HttpStatus.OK.value()),
            arguments("UNSUPPORTED_PROVIDER", HttpStatus.UNPROCESSABLE_ENTITY.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - provider={0} wasSubscriptionActive={1} expectedResponseStatus={2}")
    @MethodSource("createSubscriptionTestCases")
    void createSubscription(
        @NonNull SubscriptionPlan.Provider provider,
        Boolean wasSubscriptionActive,
        int expectedResponseStatus
    ) throws Exception {
        val providerPlanId = "provider-plan-id";
        val successUrl = "https://api.test/success";
        val cancelUrl = "https://api.test/cancel";
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(provider, providerPlanId);
        if (wasSubscriptionActive != null) {
            buildSubscription(authUser, plan, wasSubscriptionActive, false, null);
        }

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
                    eq(null),
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
            resultActions.andExpect(jsonPath("$.subscription").isMap());
            if (provider == SubscriptionPlan.Provider.STRIPE) {
                resultActions.andExpect(jsonPath("$.stripeCheckoutSessionUrl").isString());
            }
        }
    }

    static Stream<Arguments> createSubscriptionTestCases() {
        return Stream.of(
            // provider, is existing subscription active, response status
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, null, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, null, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, false, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, false, HttpStatus.CREATED.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, true, HttpStatus.CONFLICT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, true, HttpStatus.CONFLICT.value())
        );
    }

    @Test
    void listSubscriptions() throws Exception {
        val subscriptionPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val data = new HashMap<AuthUser, List<Subscription>>();
        for (int i = 0; i < 5; i++) {
            val authUser = createAuthUser(entityManager);
            val subscriptions = new ArrayList<Subscription>(5);
            for (int j = 0; j < 5; j++) {
                subscriptions.add(buildSubscription(authUser, subscriptionPlan, true, false, null));
            }

            val unstarted = buildSubscription(authUser, subscriptionPlan, false, false, null);
            unstarted.setStartAt(null);
            unstarted.setEndAt(null);
            subscriptions.add(subscriptionRepository.save(unstarted));

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
                .filter(s -> s.getStartAt() != null)
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

    @Test
    void listSubscriptions_pagination() throws Exception {
        val subscriptionPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val owner = createAuthUser(entityManager);
        for (int j = 0; j < 25; j++) {
            buildSubscription(owner, subscriptionPlan, true, false, null);
        }

        val pageSizes = Map.of(0, 20, 1, 5);
        val accessToken = createSignedAccessJwt(hmacSecret, owner, AuthTestUtils.JwtType.VALID);
        for (var entry : pageSizes.entrySet()) {
            mockMvc.perform(
                    get("/v1/subscriptions?page=" + entry.getKey())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(jsonPath("$.length()").value(entry.getValue()));
        }

        mockMvc.perform(
                get("/v1/subscriptions?page=" + 2)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(HttpStatus.OK.value()))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSubscription() throws Exception {
        val owner = createAuthUser(entityManager);
        val impersonator = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");
        val subscription = buildSubscription(owner, plan, false, false, null);

        val impersonatorToken = createSignedAccessJwt(hmacSecret, impersonator, AuthTestUtils.JwtType.VALID);
        doGetSubscriptionRequest(impersonatorToken, subscription.getId())
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

        val ownerAccessToken = createSignedAccessJwt(hmacSecret, owner, AuthTestUtils.JwtType.VALID);
        doGetSubscriptionRequest(ownerAccessToken, subscription.getId())
            .andExpect(status().is(HttpStatus.OK.value()))
            .andExpect(jsonPath("$.id").value(subscription.getId()));

        val unstartedSubscription = buildSubscription(owner, plan, false, false, null);
        unstartedSubscription.setStartAt(null);
        unstartedSubscription.setEndAt(null);
        subscriptionRepository.save(unstartedSubscription);
        doGetSubscriptionRequest(ownerAccessToken, unstartedSubscription.getId())
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    private ResultActions doGetSubscriptionRequest(String accessToken, long subscriptionId) throws Exception {
        return mockMvc.perform(get("/v1/subscriptions/" + subscriptionId)
            .header("Authorization", "Bearer " + accessToken));
    }

    @ParameterizedTest(name = "{displayName} - provider={0} isSubscriptionActive={1} expectedResponseStatus={2}")
    @MethodSource("cancelSubscriptionTestCases")
    void cancelSubscription(
        @NonNull SubscriptionPlan.Provider provider,
        boolean isSubscriptionActive,
        int expectedResponseCode
    ) throws Exception {
        val actualOwner = createAuthUser(entityManager);
        val impersonator = createAuthUser(entityManager);
        val actualOwnerAccessToken = createSignedAccessJwt(hmacSecret, actualOwner, AuthTestUtils.JwtType.VALID);
        val impersonatorAccessToken = createSignedAccessJwt(hmacSecret, impersonator, AuthTestUtils.JwtType.VALID);

        val plan = buildSubscriptionPlan(provider, "provider-plan-id");
        val subscription = buildSubscription(actualOwner, plan, isSubscriptionActive, false, "test-id");

        mockMvc.perform(
                delete("/v1/subscriptions/" + subscription.getId())
                    .header("Authorization", "Bearer " + impersonatorAccessToken))
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(
                delete("/v1/subscriptions/" + subscription.getId())
                    .header("Authorization", "Bearer " + actualOwnerAccessToken))
            .andExpect(status().is(expectedResponseCode));

        if (expectedResponseCode == HttpStatus.NO_CONTENT.value()) {
            assertFalse(subscription.isAutoRenewing());
            switch (provider) {
                case GOOGLE_PLAY:
                    verify(androidPublisherApi, times(1))
                        .cancelSubscription(subscription.getProviderSubscriptionId());
                    break;
                case STRIPE:
                    verify(stripeApi, times(1))
                        .cancelSubscription(subscription.getProviderSubscriptionId());
                    break;
                default:
                    throw new RuntimeException("unknown provider");
            }
        }
    }

    static Stream<Arguments> cancelSubscriptionTestCases() {
        return Stream.of(
            // subscription provider, is subscription active, expected response code
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, true, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, true, HttpStatus.NO_CONTENT.value()),
            arguments(SubscriptionPlan.Provider.GOOGLE_PLAY, false, HttpStatus.NOT_FOUND.value()),
            arguments(SubscriptionPlan.Provider.STRIPE, false, HttpStatus.NOT_FOUND.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - session.status={0} session.paymentStatus={1} isSubscriptionActive={2}")
    @MethodSource("handleStripeWebhookEvent_checkoutSessionCompleteTestCases")
    void handleStripeWebhookEvent_checkoutSessionComplete(
        @NonNull String sessionStatus,
        @NonNull String sessionPaymentStatus,
        boolean isSubscriptionActive,
        int expectedResponseCode
    ) throws Exception {
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-plan");
        val subscription = buildSubscription(createAuthUser(entityManager), plan, false, false, null);
        val checkoutSession = buildStripeCheckoutSession(sessionStatus, sessionPaymentStatus, String.valueOf(subscription.getId()));
        val event = buildStripeEvent("checkout.session.completed", checkoutSession);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        lenient().when(stripeApi.getSubscription(any()))
            .thenReturn(buildStripeSubscription(null, "active", plan.getProviderPlanId()));

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(expectedResponseCode));

        assertEquals(isSubscriptionActive, subscription.isActive());
        if (isSubscriptionActive) {
            assertTrue(subscription.getCustomer().isTrialPeriodUsed());
        }
    }

    static Stream<Arguments> handleStripeWebhookEvent_checkoutSessionCompleteTestCases() {
        return Stream.of(
            // session status, payment status, is subscription active, expected response code
            arguments("expired", "no_payment_required", false, HttpStatus.BAD_REQUEST.value()),
            arguments("complete", "no_payment_required", false, HttpStatus.BAD_REQUEST.value()),
            arguments("complete", "unpaid", false, HttpStatus.BAD_REQUEST.value()),
            arguments("complete", "paid", true, HttpStatus.OK.value())
        );
    }

    @ParameterizedTest(name = "{displayName} - stripeStatus={0} isSubscriptionActive={3} isPaymentPending={4}")
    @MethodSource("handleStripeWebhookEvent_subscriptionEventsTestCases")
    void handleStripeWebhookEvent_subscriptionEvents(
        @NonNull String stripeSubscriptionStatus,
        boolean wasSubscriptionActive,
        boolean wasPaymentPending,
        boolean isSubscriptionActive,
        boolean isPaymentPending
    ) throws Exception {
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription = buildSubscription(createAuthUser(entityManager), plan, wasSubscriptionActive, wasPaymentPending, stripeSubscriptionId);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, stripeSubscriptionStatus, plan.getProviderPlanId());
        val event = buildStripeEvent("customer.subscription.updated", stripeSubscription);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        when(stripeApi.getSubscription(stripeSubscriptionId))
            .thenReturn(stripeSubscription);

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertEquals(isSubscriptionActive, subscription.isActive());
        assertEquals(isPaymentPending, subscription.isPaymentPending());
    }

    static Stream<Arguments> handleStripeWebhookEvent_subscriptionEventsTestCases() {
        return Stream.of(
            // stripe subscription status, was subscription active, was payment pending, is subscription active, is payment pending
            arguments("incomplete", true, false, false, false),
            arguments("incomplete_expired", true, true, false, false),
            arguments("trialing", false, false, true, false),
            arguments("active", false, false, true, false),
            arguments("active", true, true, true, false),
            arguments("past_due", true, false, true, true),
            arguments("canceled", true, false, false, false),
            arguments("unpaid", true, true, false, false)
        );
    }

    @Test
    void handleStripeWebhookEvent_planUpgrade() throws Exception {
        val oldPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-1");
        val newPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-2");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription = buildSubscription(createAuthUser(entityManager), oldPlan, true, false, stripeSubscriptionId);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, "active", newPlan.getProviderPlanId());
        val event = buildStripeEvent("customer.subscription.updated", stripeSubscription);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        when(stripeApi.getSubscription(stripeSubscriptionId))
            .thenReturn(stripeSubscription);

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertEquals(newPlan.getProviderPlanId(), subscription.getPlan().getProviderPlanId());
    }

    @Test
    void handleStripeWebhookEvent_doublePurchase() throws Exception {
        // when user initiates purchase flow twice without completion and then goes on to complete
        // both the flows.
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription1 = buildSubscription(authUser, plan, true, false, UUID.randomUUID().toString());
        val subscription2 = buildSubscription(authUser, plan, false, false, null);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, "active", plan.getProviderPlanId());
        val checkoutSession = buildStripeCheckoutSession("complete", "paid", String.valueOf(subscription2.getId()));
        checkoutSession.setSubscription(stripeSubscriptionId);
        val event = buildStripeEvent("checkout.session.completed", checkoutSession);
        val signature = "dummy-signature";
        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        when(stripeApi.getSubscription(stripeSubscriptionId))
            .thenReturn(stripeSubscription);

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertTrue(subscription1.isActive());
        assertFalse(subscription2.isActive());
        verify(stripeApi, times(1))
            .refundSubscription(stripeSubscriptionId);
    }

    @Test
    void handleStripeWebhookEvent_customerDeletedEvent() throws Exception {
        val authUser = createAuthUser(entityManager);
        val stripeCustomer = new com.stripe.model.Customer();
        stripeCustomer.setId("stripe-customer-id");
        customerRepository.save(
            Customer.builder()
                .userId(authUser.getId())
                .stripeId(stripeCustomer.getId())
                .build());

        val signature = "dummy-signature";
        val event = buildStripeEvent("customer.deleted", stripeCustomer);
        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        mockMvc.perform(post("/v1/subscriptions/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event.toJson()))
            .andExpect(status().is(HttpStatus.OK.value()));

        assertNull(customerRepository.findById(authUser.getId()).orElseThrow().getStripeId());
    }

    @NonNull
    private SubscriptionPlan buildSubscriptionPlan(@NonNull SubscriptionPlan.Provider provider, @NonNull String providerPlanId) {
        val plan = SubscriptionPlan.builder()
            .provider(provider)
            .providerPlanId(providerPlanId)
            .billingPeriodMonths((short) 1)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(10000)
            .build();

        entityManager.persist(plan);
        return plan;
    }

    @NonNull
    private Subscription buildSubscription(
        @NonNull AuthUser owner,
        @NonNull SubscriptionPlan plan,
        boolean isActive,
        boolean isPaymentPending,
        String providerSubscriptionId
    ) {
        return subscriptionRepository.save(
            Subscription.builder()
                .customer(
                    customerRepository.save(
                        Customer.builder()
                            .userId(owner.getId())
                            .build()))
                .plan(plan)
                .providerSubscriptionId(providerSubscriptionId)
                .isPaymentPending(isPaymentPending)
                .startAt(OffsetDateTime.now().plusHours(-2))
                .endAt(OffsetDateTime.now().plusHours(isActive ? 2 : -1))
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
    private static Session buildStripeCheckoutSession(@NonNull String status, @NonNull String paymentStatus, String clientReferenceId) {
        val session = new Session();
        session.setMode("subscription");
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setSubscription(UUID.randomUUID().toString());
        session.setCustomer(UUID.randomUUID().toString());
        session.setClientReferenceId(clientReferenceId);
        return session;
    }

    @NonNull
    private static com.stripe.model.Subscription buildStripeSubscription(String id, @NonNull String status, @NonNull String priceId) {
        val subscription = new com.stripe.model.Subscription();
        subscription.setId(id);
        subscription.setStatus(status);

        val now = OffsetDateTime.now().toEpochSecond();
        subscription.setCurrentPeriodStart(now);
        subscription.setStartDate(now);
        subscription.setCurrentPeriodEnd(now + 60 * 60);

        val items = new SubscriptionItemCollection();
        items.setData(List.of(buildSubscriptionItem(priceId)));
        subscription.setItems(items);
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
