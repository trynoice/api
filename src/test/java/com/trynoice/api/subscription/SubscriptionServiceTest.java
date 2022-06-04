package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.cache.Cache;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SubscriptionServiceTest {

    @Mock
    private SubscriptionConfiguration subscriptionConfiguration;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private AccountServiceContract accountServiceContract;

    @Mock
    private AndroidPublisherApi androidPublisherApi;

    @Mock
    private StripeApi stripeApi;

    @Mock
    private Cache cache;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
            subscriptionConfiguration,
            customerRepository,
            subscriptionPlanRepository,
            subscriptionRepository,
            new ObjectMapper(),
            accountServiceContract,
            androidPublisherApi,
            stripeApi,
            cache);
    }

    @Test
    void listPlans_withSupportedProviders() throws UnsupportedSubscriptionPlanProviderException {
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "google_plan_plan_id");
        googlePlayPlan.setId((short) 1);

        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "stripe_plan_id");
        stripePlan.setId((short) 2);

        lenient()
            .when(subscriptionPlanRepository.findAll(any(Sort.class)))
            .thenReturn(List.of(googlePlayPlan, stripePlan));

        lenient()
            .when(subscriptionPlanRepository.findAllByProvider(eq(SubscriptionPlan.Provider.GOOGLE_PLAY), any()))
            .thenReturn(List.of(googlePlayPlan));

        lenient()
            .when(subscriptionPlanRepository.findAllByProvider(eq(SubscriptionPlan.Provider.STRIPE), any()))
            .thenReturn(List.of(stripePlan));

        val testCases = new HashMap<SubscriptionPlan.Provider, List<SubscriptionPlan>>();
        testCases.put(null, List.of(googlePlayPlan, stripePlan));
        testCases.put(SubscriptionPlan.Provider.GOOGLE_PLAY, List.of(googlePlayPlan));
        testCases.put(SubscriptionPlan.Provider.STRIPE, List.of(stripePlan));

        for (val entry : testCases.entrySet()) {
            val provider = entry.getKey();
            val result = entry.getValue();
            val plans = service.listPlans(provider == null ? null : provider.name());

            assertEquals(result.size(), plans.size());
            for (int i = 0; i < result.size(); i++) {
                val expecting = result.get(i);
                val got = plans.get(i);

                assertEquals(expecting.getId(), got.getId());
                assertEquals(expecting.getProvider().name().toLowerCase(), got.getProvider().toLowerCase());
                assertEquals(expecting.getBillingPeriodMonths(), got.getBillingPeriodMonths());
                assertEquals(expecting.getTrialPeriodDays(), got.getTrialPeriodDays());
                assertEquals(expecting.getPriceInIndianPaise(), got.getPriceInIndianPaise());
                assertEquals(
                    expecting.getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                        ? expecting.getProviderPlanId()
                        : null,
                    got.getGooglePlaySubscriptionId());
            }
        }
    }

    @Test
    void listPlans_withUnsupportedProvider() {
        assertThrows(
            UnsupportedSubscriptionPlanProviderException.class,
            () -> service.listPlans("unsupported-provider"));
    }

    @Test
    void createSubscription_withExistingActiveSubscription() {
        val userId = 1L;
        when(subscriptionPlanRepository.findById((short) 1))
            .thenReturn(Optional.of(buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id")));

        when(subscriptionRepository.existsActiveByCustomerUserId(userId))
            .thenReturn(true);

        val params = new SubscriptionFlowParams((short) 1, "success-url", "cancel-url");
        assertThrows(DuplicateSubscriptionException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withInvalidPlanId() {
        val userId = 1L;
        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.empty());
        assertThrows(SubscriptionPlanNotFoundException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withStripeApiError() throws Exception {
        val userId = 1L;
        val stripePriceId = "stripe-price-id-1";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findById(planId))
            .thenReturn(Optional.of(plan));

        when(customerRepository.findById(userId))
            .thenReturn(Optional.of(Customer.builder().userId(userId).build()));

        val subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer((Answer<Subscription>) invocation -> subscriptionCaptor.getValue());

        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new ApiConnectionException("test-error"));

        assertThrows(RuntimeException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withValidParams() throws Exception {
        val userId = 1L;
        val stripePriceId = "stripe-price-id-1";
        val stripeCustomerId = "stripe-customer-id";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findById(planId))
            .thenReturn(Optional.of(plan));

        val subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer((Answer<Subscription>) invocation -> subscriptionCaptor.getValue());

        when(customerRepository.findById(userId))
            .thenReturn(Optional.of(Customer.builder()
                .userId(userId)
                .stripeId(stripeCustomerId)
                .build()));

        val redirectUrl = "test-redirect-url";
        val mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn(redirectUrl);

        when(
            stripeApi.createCheckoutSession(
                eq(params.getSuccessUrl()),
                eq(params.getCancelUrl()),
                eq(stripePriceId),
                any(),
                eq(null),
                eq(stripeCustomerId),
                any()))
            .thenReturn(mockSession);

        val result = service.createSubscription(userId, params);
        assertNotNull(result);
        assertEquals(redirectUrl, result.getStripeCheckoutSessionUrl());
    }

    @Test
    void listSubscriptions() throws StripeException {
        val userId1 = 1L;
        val userId2 = 2L;
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");
        val subscription1 = buildSubscription(userId1, plan, true, true);
        val subscription2 = buildSubscription(userId2, plan, false, false);

        lenient().when(subscriptionRepository.findAllStartedByCustomerUserId(eq(userId1), any()))
            .thenReturn(new PageImpl<>(List.of(subscription1)));

        lenient().when(subscriptionRepository.findAllStartedByCustomerUserId(eq(userId2), any()))
            .thenReturn(new PageImpl<>(List.of(subscription2)));

        lenient().when(stripeApi.createCustomerPortalSession(any(), any()))
            .thenReturn(mock(com.stripe.model.billingportal.Session.class));

        val result1 = service.listSubscriptions(userId1, false, null, 0);
        assertEquals(1, result1.size());
        assertEquals(subscription1.getId(), result1.get(0).getId());

        val result2 = service.listSubscriptions(userId2, false, null, 0);
        assertEquals(1, result2.size());
        assertEquals(subscription2.getId(), result2.get(0).getId());
    }

    @Test
    void getSubscription() {
        // skipped unit tests, wrote integration tests instead.
    }

    @ParameterizedTest(name = "{displayName} #{index}")
    @MethodSource("cancelSubscriptionTestCases")
    <T extends Throwable> void cancelSubscription(
        @NonNull Subscription subscription,
        @NonNull Long principalId,
        Class<T> expectedException
    ) throws IOException, StripeException {
        when(subscriptionRepository.findById(subscription.getId()))
            .thenReturn(Optional.of(subscription));

        if (expectedException != null) {
            assertThrows(expectedException, () -> service.cancelSubscription(principalId, subscription.getId()));
        } else {
            assertDoesNotThrow(() -> service.cancelSubscription(principalId, subscription.getId()));

            switch (subscription.getPlan().getProvider()) {
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
        val userId1 = 1L;
        val userId2 = 2L;
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");

        return Stream.of(
            // subscription, principalId, expected exception
            arguments(buildSubscription(userId1, googlePlayPlan, true, false), userId1, null),
            arguments(buildSubscription(userId1, stripePlan, true, false), userId1, null),
            arguments(buildSubscription(userId2, googlePlayPlan, true, false), userId1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, stripePlan, true, false), userId2, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, googlePlayPlan, false, false), userId1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, stripePlan, false, false), userId1, SubscriptionNotFoundException.class)
        );
    }

    @Test
    void handleGooglePlayWebhookEvent() {
        // The unit test quickly becomes too complex due to all the mocking. The best case scenario
        // here is to try covering as many cases in integration tests as possible.
    }

    @Test
    void handleStripeWebhookEvent() {
        // skipped unit tests, wrote integration tests instead.
    }

    @Test
    void isUserSubscribed() {
        val testCases = Map.of(
            1L, true,
            2L, false
        );

        testCases.forEach((userId, isSubscribed) ->
            when(subscriptionRepository.existsActiveByCustomerUserId(userId))
                .thenReturn(isSubscribed));

        testCases.forEach((userId, isSubscribed) ->
            assertEquals(isSubscribed, service.isUserSubscribed(userId)));
    }

    @NonNull
    private static SubscriptionPlan buildSubscriptionPlan(@NonNull SubscriptionPlan.Provider provider, @NonNull String providerPlanId) {
        val plan = SubscriptionPlan.builder()
            .provider(provider)
            .providerPlanId(providerPlanId)
            .billingPeriodMonths((short) 2)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(22500)
            .build();

        plan.setId((short) Math.round(Math.random() * 1000));
        return plan;
    }

    @NonNull
    private static Subscription buildSubscription(
        @NonNull Long ownerId,
        @NonNull SubscriptionPlan plan,
        boolean isActive,
        boolean isPaymentPending
    ) {
        val now = OffsetDateTime.now();
        return Subscription.builder()
            .id(Math.round(Math.random() * 1000))
            .customer(Customer.builder().userId(ownerId).build())
            .plan(plan)
            .providerSubscriptionId(UUID.randomUUID().toString())
            .isPaymentPending(isPaymentPending)
            .startAt(now.plusHours(-2))
            .endAt(now.plusHours(isActive ? 2 : -1))
            .build();
    }
}
