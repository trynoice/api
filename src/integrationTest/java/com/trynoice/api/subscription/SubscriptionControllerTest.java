package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.ecb.ForeignExchangeRatesProvider;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.GiftCardRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionPlanRepository;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionPlanResponse;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.trynoice.api.subscription.SubscriptionTestUtils.buildCustomer;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildGiftCard;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildStripeCheckoutSession;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildStripeEvent;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildStripeSubscription;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildSubscription;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildSubscriptionPlan;
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

    @Autowired
    private GiftCardRepository giftCardRepository;

    @MockBean
    private AndroidPublisherApi androidPublisherApi;

    @MockBean
    private StripeApi stripeApi;

    @MockBean
    private ForeignExchangeRatesProvider exchangeRatesProvider;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @MethodSource("listPlansTestCases")
    void listPlans(String provider, String currency, int expectedResponseStatus) throws Exception {
        val request = get("/v1/subscriptions/plans");
        if (provider != null) {
            request.queryParam("provider", provider);
        }

        if (currency != null) {
            request.queryParam("currency", currency);
            when(exchangeRatesProvider.getRateForCurrency(any(), eq(currency)))
                .thenReturn(Optional.of(1.0));
        }

        val result = mockMvc.perform(request)
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val plans = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                SubscriptionPlanResponse[].class);

            assertNotEquals(0, plans.length);
            if (provider != null) {
                assertTrue(Arrays.stream(plans).allMatch(p -> provider.equalsIgnoreCase(p.getProvider())));
            }

            if (currency != null) {
                assertTrue(Arrays.stream(plans).allMatch(p -> p.getPriceInRequestedCurrency() != null));
                assertTrue(Arrays.stream(plans).allMatch(p -> p.getRequestedCurrencyCode() != null));
            }
        }
    }

    static Stream<Arguments> listPlansTestCases() {
        return Stream.of(
            // provider, currency, expected response code
            arguments(null, null, HttpStatus.OK.value()),
            arguments("GOOGLE_PLAY", "USD", HttpStatus.OK.value()),
            arguments("STRIPE", "EUR", HttpStatus.OK.value()),
            arguments("UNSUPPORTED_PROVIDER", null, HttpStatus.UNPROCESSABLE_ENTITY.value())
        );
    }

    @ParameterizedTest
    @MethodSource("createSubscriptionTestCases")
    void createSubscription(
        @NonNull SubscriptionPlan.Provider provider,
        Boolean wasSubscriptionActive,
        int expectedResponseStatus
    ) throws Exception {
        val providedId = "provided-id";
        val successUrl = "https://api.test/success";
        val cancelUrl = "https://api.test/cancel";
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(entityManager, provider, providedId);
        if (wasSubscriptionActive != null) {
            buildSubscription(entityManager, authUser, plan, wasSubscriptionActive, false, null);
        }

        val mockSession = mock(Session.class);
        val sessionUrl = "/checkout-session-url";
        if (provider == SubscriptionPlan.Provider.STRIPE) {
            val customer = buildCustomer(entityManager, authUser);
            when(mockSession.getUrl()).thenReturn(sessionUrl);
            when(
                stripeApi.createCheckoutSession(
                    eq(successUrl),
                    eq(cancelUrl),
                    eq(plan.getProvidedId()),
                    any(),
                    eq(null),
                    eq(customer.getStripeId()),
                    any()))
                .thenReturn(mockSession);
        }

        val params = objectMapper.writeValueAsString(new SubscriptionFlowParams(plan.getId(), successUrl, cancelUrl));
        val resultActions = mockMvc.perform(
                post("/v1/subscriptions")
                    .header("Authorization", "bearer " + createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(params))
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
        val subscriptionPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val data = new HashMap<AuthUser, List<Subscription>>();
        for (int i = 0; i < 5; i++) {
            val authUser = createAuthUser(entityManager);
            val subscriptions = new ArrayList<Subscription>(5);
            for (int j = 0; j < 5; j++) {
                subscriptions.add(buildSubscription(entityManager, authUser, subscriptionPlan, true, false, null));
            }

            val unstarted = buildSubscription(entityManager, authUser, subscriptionPlan, false, false, null);
            unstarted.setStartAt(null);
            unstarted.setEndAt(null);
            subscriptions.add(subscriptionRepository.save(unstarted));

            data.put(authUser, subscriptions);
        }

        for (val entry : data.entrySet()) {
            val testReturnUrl = "https://test-return-url";
            val testCustomerPortalUrl = "test-customer-portal-url";
            val addCurrencyParam = entry.getKey().getId() % 2 == 0;
            val mockSession = mock(com.stripe.model.billingportal.Session.class);
            lenient().when(mockSession.getUrl()).thenReturn(testCustomerPortalUrl);

            val customer = buildCustomer(entityManager, entry.getKey());
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
            val requestBuilder = get("/v1/subscriptions")
                .queryParam("stripeReturnUrl", testReturnUrl)
                .header("Authorization", "Bearer " + accessToken);

            if (addCurrencyParam) {
                val currency = "USD";
                requestBuilder.queryParam("currency", currency);
                when(exchangeRatesProvider.getRateForCurrency(any(), eq(currency)))
                    .thenReturn(Optional.of(1.0));
            }

            val result = mockMvc.perform(requestBuilder)
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
                val priceInRequestedCurrencyNode = actualSubscription.at("/plan/priceInRequestedCurrency");
                if (actualIsActive && actualProvider.equalsIgnoreCase(SubscriptionPlan.Provider.STRIPE.name())) {
                    assertEquals(testCustomerPortalUrl, stripeCustomerPortalUrlNode.asText());
                } else {
                    assertTrue(stripeCustomerPortalUrlNode.isMissingNode() || stripeCustomerPortalUrlNode.isNull());
                }

                if (addCurrencyParam) {
                    assertTrue(priceInRequestedCurrencyNode.isNumber());
                } else {
                    assertTrue(priceInRequestedCurrencyNode.isMissingNode() || priceInRequestedCurrencyNode.isNull());
                }
            }
        }
    }

    @Test
    void listSubscriptions_pagination() throws Exception {
        val subscriptionPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val owner = createAuthUser(entityManager);
        for (int j = 0; j < 25; j++) {
            buildSubscription(entityManager, owner, subscriptionPlan, true, false, null);
        }

        val pageSizes = Map.of(0, 20, 1, 5);
        val accessToken = createSignedAccessJwt(hmacSecret, owner, AuthTestUtils.JwtType.VALID);
        for (var entry : pageSizes.entrySet()) {
            mockMvc.perform(
                    get("/v1/subscriptions")
                        .queryParam("page", entry.getKey().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(jsonPath("$.length()").value(entry.getValue()));
        }

        mockMvc.perform(
                get("/v1/subscriptions")
                    .queryParam("page", "2")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(HttpStatus.OK.value()))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSubscription() throws Exception {
        val owner = createAuthUser(entityManager);
        val impersonator = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "test-provider-id");
        val subscription = buildSubscription(entityManager, owner, plan, false, false, null);

        val impersonatorToken = createSignedAccessJwt(hmacSecret, impersonator, AuthTestUtils.JwtType.VALID);
        doGetSubscriptionRequest(impersonatorToken, subscription.getId(), null)
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));

        val ownerAccessToken = createSignedAccessJwt(hmacSecret, owner, AuthTestUtils.JwtType.VALID);
        doGetSubscriptionRequest(ownerAccessToken, subscription.getId(), null)
            .andExpect(status().is(HttpStatus.OK.value()))
            .andExpect(jsonPath("$.id").value(subscription.getId()));

        val currency = "USD";
        when(exchangeRatesProvider.getRateForCurrency(any(), eq(currency)))
            .thenReturn(Optional.of(1.0));
        doGetSubscriptionRequest(ownerAccessToken, subscription.getId(), currency)
            .andExpect(status().is(HttpStatus.OK.value()))
            .andExpect(jsonPath("$.plan.priceInRequestedCurrency").isNumber());

        val unstartedSubscription = buildSubscription(entityManager, owner, plan, false, false, null);
        unstartedSubscription.setStartAt(null);
        unstartedSubscription.setEndAt(null);
        subscriptionRepository.save(unstartedSubscription);
        doGetSubscriptionRequest(ownerAccessToken, unstartedSubscription.getId(), null)
            .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    private ResultActions doGetSubscriptionRequest(String accessToken, long subscriptionId, String currency) throws Exception {
        val requestBuilder = get("/v1/subscriptions/{subscriptionId}", subscriptionId)
            .header("Authorization", "Bearer " + accessToken);
        if (currency != null) {
            requestBuilder.queryParam("currency", currency);
        }

        return mockMvc.perform(requestBuilder);
    }

    @ParameterizedTest
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

        val plan = buildSubscriptionPlan(entityManager, provider, "provider-plan-id");
        val subscription = buildSubscription(entityManager, actualOwner, plan, isSubscriptionActive, false, "test-id");

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
                        .cancelSubscription(subscription.getProvidedId());
                    break;
                case STRIPE:
                    verify(stripeApi, times(1))
                        .cancelSubscription(subscription.getProvidedId());
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

    @ParameterizedTest
    @MethodSource("handleStripeWebhookEvent_checkoutSessionCompleteTestCases")
    void handleStripeWebhookEvent_checkoutSessionComplete(
        @NonNull String sessionStatus,
        @NonNull String sessionPaymentStatus,
        boolean isSubscriptionActive,
        int expectedResponseCode
    ) throws Exception {
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "test-plan");
        val subscription = buildSubscription(entityManager, createAuthUser(entityManager), plan, false, false, null);
        val checkoutSession = buildStripeCheckoutSession(sessionStatus, sessionPaymentStatus, String.valueOf(subscription.getId()));
        val event = buildStripeEvent("checkout.session.completed", checkoutSession);
        val signature = "dummy-signature";

        when(stripeApi.decodeWebhookPayload(eq(event.toJson()), eq(signature), any()))
            .thenReturn(event);

        lenient().when(stripeApi.getSubscription(any()))
            .thenReturn(buildStripeSubscription(null, "active", plan.getProvidedId()));

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

    @ParameterizedTest
    @MethodSource("handleStripeWebhookEvent_subscriptionEventsTestCases")
    void handleStripeWebhookEvent_subscriptionEvents(
        @NonNull String stripeSubscriptionStatus,
        boolean wasSubscriptionActive,
        boolean wasPaymentPending,
        boolean isSubscriptionActive,
        boolean isPaymentPending
    ) throws Exception {
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "provider-plan-id");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription = buildSubscription(entityManager, createAuthUser(entityManager), plan, wasSubscriptionActive, wasPaymentPending, stripeSubscriptionId);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, stripeSubscriptionStatus, plan.getProvidedId());
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
        val oldPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "provider-plan-1");
        val newPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "provider-plan-2");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription = buildSubscription(entityManager, createAuthUser(entityManager), oldPlan, true, false, stripeSubscriptionId);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, "active", newPlan.getProvidedId());
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

        assertEquals(newPlan.getProvidedId(), subscription.getPlan().getProvidedId());
    }

    @Test
    void handleStripeWebhookEvent_doublePurchase() throws Exception {
        // when user initiates purchase flow twice without completion and then goes on to complete
        // both the flows.
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "provider-plan-id");
        val stripeSubscriptionId = UUID.randomUUID().toString();
        val subscription1 = buildSubscription(entityManager, authUser, plan, true, false, UUID.randomUUID().toString());
        val subscription2 = buildSubscription(entityManager, authUser, plan, false, false, null);
        val stripeSubscription = buildStripeSubscription(stripeSubscriptionId, "active", plan.getProvidedId());
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

    @ParameterizedTest
    @MethodSource("getStripeCustomerPortalUrlTestCases")
    void getStripeCustomerPortalUrl(boolean exists, String stripeId, String returnUrl, int expectedResponseStatus) throws Exception {
        val user = createAuthUser(entityManager);
        if (exists) {
            val customer = buildCustomer(entityManager, user);
            customer.setStripeId(stripeId);
            entityManager.persist(customer);
        }

        val customerPortalUrl = "https://api.test/customer-portal-url";
        val customerPortalSession = mock(com.stripe.model.billingportal.Session.class);
        when(customerPortalSession.getUrl()).thenReturn(customerPortalUrl);
        lenient().when(stripeApi.createCustomerPortalSession(stripeId, returnUrl)).thenReturn(customerPortalSession);

        val accessToken = createSignedAccessJwt(hmacSecret, user, AuthTestUtils.JwtType.VALID);
        val resultActions = mockMvc.perform(
                get("/v1/subscriptions/stripe/customerPortalUrl")
                    .header("Authorization", "Bearer " + accessToken)
                    .queryParam("returnUrl", returnUrl))
            .andExpect(status().is(expectedResponseStatus));

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            resultActions.andExpect(jsonPath("$.url").value(customerPortalUrl));
        }
    }

    static Stream<Arguments> getStripeCustomerPortalUrlTestCases() {
        return Stream.of(
            // customer exists, stripe customer id, return url, expected response code
            arguments(false, null, null, 400),
            arguments(false, null, "https://api.test/return-url", 404),
            arguments(true, null, "https://api.test/return-url", 404),
            arguments(true, "test-stripe-id", "https://api.test/return-url", 200)
        );
    }

    @ParameterizedTest
    @MethodSource("getGiftCardTestCases")
    void getGiftCard(boolean exists, Boolean owned, int expectedResponseStatus) throws Exception {
        val code = "test-gift-card-1";
        val authUser = createAuthUser(entityManager);
        if (exists) {
            val owner = owned == null ? null : buildCustomer(entityManager, owned ? authUser : createAuthUser(entityManager));
            buildGiftCard(entityManager, code, owner, false, false);
        }

        val accessToken = createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID);
        mockMvc.perform(
                get("/v1/subscriptions/giftCards/{code}", code)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> getGiftCardTestCases() {
        return Stream.of(
            // exists, owned, response status
            arguments(false, null, HttpStatus.NOT_FOUND.value()),
            arguments(true, null, HttpStatus.OK.value()),
            arguments(true, false, HttpStatus.NOT_FOUND.value()),
            arguments(true, true, HttpStatus.OK.value())
        );
    }

    @ParameterizedTest
    @MethodSource("redeemGiftCardTestCases")
    void redeemGiftCard(
        boolean exists,
        Boolean owned,
        boolean redeemed,
        Boolean expired,
        boolean subscribed,
        int expectedResponseStatus
    ) throws Exception {
        val code = "test-gift-card-2";
        val authUser = createAuthUser(entityManager);
        if (exists) {
            val owner = owned == null ? null : buildCustomer(entityManager, owned ? authUser : createAuthUser(entityManager));
            buildGiftCard(entityManager, code, owner, redeemed, expired);
        }

        if (subscribed) {
            val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "test-plan");
            buildSubscription(entityManager, authUser, plan, true, false, "test-sub");
        }

        val accessToken = createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID);
        mockMvc.perform(
                post("/v1/subscriptions/giftCards/{code}/redeem", code)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().is(expectedResponseStatus));
    }

    static Stream<Arguments> redeemGiftCardTestCases() {
        return Stream.of(
            // exists, owned, redeemed, expired, subscribed, response status
            arguments(false, null, false, false, false, HttpStatus.NOT_FOUND.value()),
            arguments(true, false, false, false, false, HttpStatus.NOT_FOUND.value()),
            arguments(true, null, false, false, false, HttpStatus.CREATED.value()),
            arguments(true, true, false, false, false, HttpStatus.CREATED.value()),
            arguments(true, null, true, false, false, HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(true, true, true, false, false, HttpStatus.UNPROCESSABLE_ENTITY.value()),
            arguments(true, false, true, false, false, HttpStatus.NOT_FOUND.value()),
            arguments(true, true, false, true, false, HttpStatus.GONE.value()),
            arguments(true, true, false, null, false, HttpStatus.CREATED.value()),
            arguments(true, true, false, false, true, HttpStatus.CONFLICT.value())
        );
    }
}
