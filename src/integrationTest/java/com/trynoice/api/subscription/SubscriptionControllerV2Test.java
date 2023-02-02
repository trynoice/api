package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.GiftCardRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionPlanRepository;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.upstream.ForeignExchangeRatesProvider;
import com.trynoice.api.subscription.upstream.StripeApi;
import com.trynoice.api.testing.AuthTestUtils;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.trynoice.api.subscription.SubscriptionTestUtils.buildCustomer;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildGiftCard;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildSubscription;
import static com.trynoice.api.subscription.SubscriptionTestUtils.buildSubscriptionPlan;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createSignedAccessJwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SubscriptionControllerV2Test {

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
    private StripeApi stripeApi;

    @MockBean
    private ForeignExchangeRatesProvider exchangeRatesProvider;

    @Autowired
    private MockMvc mockMvc;

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
                    any(),
                    eq(null),
                    eq(customer.getStripeId()),
                    any()))
                .thenReturn(mockSession);
        }

        val params = objectMapper.writeValueAsString(new SubscriptionFlowParams(plan.getId(), successUrl, cancelUrl));
        val resultActions = mockMvc.perform(
                post("/v2/subscriptions")
                    .header("Authorization", "bearer " + createSignedAccessJwt(hmacSecret, authUser, AuthTestUtils.JwtType.VALID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(params))
            .andExpect(status().is(expectedResponseStatus));

        if (expectedResponseStatus == HttpStatus.CREATED.value()) {
            resultActions.andExpect(jsonPath("$.subscriptionId").isNumber());
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
            val addCurrencyParam = entry.getKey().getId() % 2 == 0;
            val accessToken = createSignedAccessJwt(hmacSecret, entry.getKey(), AuthTestUtils.JwtType.VALID);
            val requestBuilder = get("/v2/subscriptions")
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
                val priceInRequestedCurrencyNode = actualSubscription.at("/plan/priceInRequestedCurrency");
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
                    get("/v2/subscriptions")
                        .queryParam("page", entry.getKey().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(HttpStatus.OK.value()))
                .andExpect(jsonPath("$.length()").value(entry.getValue()));
        }

        mockMvc.perform(
                get("/v2/subscriptions")
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
        val requestBuilder = get("/v2/subscriptions/{subscriptionId}", subscriptionId)
            .header("Authorization", "Bearer " + accessToken);
        if (currency != null) {
            requestBuilder.queryParam("currency", currency);
        }

        return mockMvc.perform(requestBuilder);
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
                post("/v2/subscriptions/giftCards/{code}/redeem", code)
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
