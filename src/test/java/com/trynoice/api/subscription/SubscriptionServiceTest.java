package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionStateException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private AndroidPublisherApi androidPublisherApi;

    @Mock
    private StripeApi stripeApi;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
            subscriptionConfiguration,
            subscriptionPlanRepository,
            subscriptionRepository,
            new ObjectMapper(),
            androidPublisherApi,
            stripeApi);
    }

    @Test
    void getPlans_withSupportedProviders() throws UnsupportedSubscriptionPlanProviderException {
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "google_plan_plan_id");
        googlePlayPlan.setId((short) 1);

        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "stripe_plan_id");
        stripePlan.setId((short) 2);

        lenient()
            .when(subscriptionPlanRepository.findAllActive())
            .thenReturn(List.of(googlePlayPlan, stripePlan));

        lenient()
            .when(subscriptionPlanRepository.findAllActiveByProvider(SubscriptionPlan.Provider.GOOGLE_PLAY))
            .thenReturn(List.of(googlePlayPlan));

        lenient()
            .when(subscriptionPlanRepository.findAllActiveByProvider(SubscriptionPlan.Provider.STRIPE))
            .thenReturn(List.of(stripePlan));

        val testCases = new HashMap<SubscriptionPlan.Provider, List<SubscriptionPlan>>();
        testCases.put(null, List.of(googlePlayPlan, stripePlan));
        testCases.put(SubscriptionPlan.Provider.GOOGLE_PLAY, List.of(googlePlayPlan));
        testCases.put(SubscriptionPlan.Provider.STRIPE, List.of(stripePlan));

        for (val entry : testCases.entrySet()) {
            val provider = entry.getKey();
            val result = entry.getValue();
            val plans = service.getPlans(provider == null ? null : provider.name());

            assertEquals(result.size(), plans.size());
            for (int i = 0; i < result.size(); i++) {
                val expecting = result.get(i);
                val got = plans.get(i);

                assertEquals(expecting.getId(), got.getId());
                assertEquals(expecting.getProvider().name().toLowerCase(), got.getProvider().toLowerCase());
                assertEquals(expecting.getBillingPeriodMonths(), got.getBillingPeriodMonths());
                assertEquals(expecting.getTrialPeriodDays(), got.getTrialPeriodDays());
                assertTrue(got.getPriceInr().contains("" + (expecting.getPriceInIndianPaise() / 100)));
            }
        }

    }

    @Test
    void getPlans_withUnsupportedProvider() {
        assertThrows(
            UnsupportedSubscriptionPlanProviderException.class,
            () -> service.getPlans("unsupported-provider"));
    }

    @Test
    void createSubscription_withExistingActiveSubscription() {
        val authUser = buildAuthUser();
        when(subscriptionPlanRepository.findActiveById((short) 1))
            .thenReturn(Optional.of(buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id")));

        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(mock(Subscription.class)));

        val params = new SubscriptionFlowParams((short) 1, "success-url", "cancel-url");
        assertThrows(DuplicateSubscriptionException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withInvalidPlanId() {
        val authUser = buildAuthUser();
        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");
        when(subscriptionPlanRepository.findActiveById(planId)).thenReturn(Optional.empty());
        assertThrows(SubscriptionPlanNotFoundException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withStripeApiError() throws Exception {
        val authUser = buildAuthUser();
        val stripePriceId = "stripe-price-id-1";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);
        val subscription = buildSubscription(authUser, plan, Subscription.Status.CREATED);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(plan));

        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(subscription));

        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new ApiConnectionException("test-error"));

        assertThrows(RuntimeException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withValidParams() throws Exception {
        val authUser = buildAuthUser();
        val stripePriceId = "stripe-price-id-1";
        val stripeCustomerId = "stripe-customer-id";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);
        val subscription = buildSubscription(authUser, plan, Subscription.Status.CREATED);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(plan));

        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(subscription));

        when(subscriptionRepository.findActiveStripeCustomerIdByOwner(authUser))
            .thenReturn(Optional.of(stripeCustomerId));

        val redirectUrl = "test-redirect-url";
        val mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn(redirectUrl);

        when(
            stripeApi.createCheckoutSession(
                eq(params.getSuccessUrl()),
                eq(params.getCancelUrl()),
                eq(stripePriceId),
                eq(subscription.getId().toString()),
                eq(authUser.getEmail()),
                eq(stripeCustomerId),
                any()))
            .thenReturn(mockSession);

        val result = service.createSubscription(authUser, params);
        assertNotNull(result);
        assertEquals(subscription.getId(), result.getSubscriptionId());
        assertEquals(redirectUrl, result.getStripeCheckoutSessionUrl());
    }

    @Test
    void getSubscriptions() throws StripeException {
        val authUser1 = buildAuthUser();
        val authUser2 = buildAuthUser();
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");
        val subscription1 = buildSubscription(authUser1, plan, Subscription.Status.ACTIVE);
        val subscription2 = buildSubscription(authUser2, plan, Subscription.Status.INACTIVE);

        lenient().when(subscriptionRepository.findAllActiveByOwnerAndStatus(eq(authUser1), any()))
            .thenReturn(List.of(subscription1));

        lenient().when(subscriptionRepository.findAllActiveByOwnerAndStatus(eq(authUser2), any()))
            .thenReturn(List.of(subscription2));

        lenient().when(stripeApi.createCustomerPortalSession(any(), any()))
            .thenReturn(mock(com.stripe.model.billingportal.Session.class));

        val result1 = service.getSubscriptions(authUser1, false, null);
        assertEquals(1, result1.size());
        assertEquals(subscription1.getId(), result1.get(0).getId());

        val result2 = service.getSubscriptions(authUser2, false, null);
        assertEquals(1, result2.size());
        assertEquals(subscription2.getId(), result2.get(0).getId());
    }

    @ParameterizedTest(name = "{displayName} #{index}")
    @MethodSource("cancelSubscriptionTestCases")
    <T extends Throwable> void cancelSubscription(
        @NonNull Subscription subscription,
        @NonNull AuthUser principal,
        Class<T> expectedException
    ) throws IOException, StripeException {
        when(subscriptionRepository.findActiveById(subscription.getId()))
            .thenReturn(Optional.of(subscription));

        if (expectedException != null) {
            assertThrows(expectedException, () -> service.cancelSubscription(principal, subscription.getId()));
        } else {
            assertDoesNotThrow(() -> service.cancelSubscription(principal, subscription.getId()));

            switch (subscription.getPlan().getProvider()) {
                case GOOGLE_PLAY:
                    verify(androidPublisherApi, times(1))
                        .cancelSubscription(
                            any(),
                            eq(subscription.getPlan().getProviderPlanId()),
                            eq(subscription.getProviderSubscriptionId()));
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
        val authUser1 = buildAuthUser();
        val authUser2 = buildAuthUser();
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");
        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");

        return Stream.of(
            // subscription, principal, expected exception
            arguments(buildSubscription(authUser1, googlePlayPlan, Subscription.Status.ACTIVE), authUser1, null),
            arguments(buildSubscription(authUser1, stripePlan, Subscription.Status.ACTIVE), authUser1, null),
            arguments(buildSubscription(authUser2, googlePlayPlan, Subscription.Status.ACTIVE), authUser1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(authUser1, stripePlan, Subscription.Status.ACTIVE), authUser2, SubscriptionNotFoundException.class),
            arguments(buildSubscription(authUser1, googlePlayPlan, Subscription.Status.CREATED), authUser1, SubscriptionStateException.class),
            arguments(buildSubscription(authUser1, stripePlan, Subscription.Status.INACTIVE), authUser1, SubscriptionStateException.class)
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

    @ParameterizedTest(name = "{displayName} - isUserSubscribed={2}")
    @MethodSource("isUserSubscribedTestCases")
    void isUserSubscribed(@NonNull AuthUser authUser, Subscription subscription, boolean isUserSubscribed) {
        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.ofNullable(subscription));

        assertEquals(isUserSubscribed, service.isUserSubscribed(authUser));
    }

    static Stream<Arguments> isUserSubscribedTestCases() {
        val authUser = buildAuthUser();
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provider-id");

        return Stream.of(
            // subscription, expected response
            arguments(authUser, null, false),
            arguments(authUser, buildSubscription(authUser, plan, Subscription.Status.ACTIVE), true),
            arguments(authUser, buildSubscription(authUser, plan, Subscription.Status.PENDING), true)
        );
    }

    @NonNull
    private static AuthUser buildAuthUser() {
        val authUser = AuthUser.builder()
            .name("test-name")
            .email("test-name@api.test")
            .build();

        authUser.setId(Math.round(Math.random() * 10000));
        return authUser;
    }

    @NonNull
    private static SubscriptionPlan buildSubscriptionPlan(@NonNull SubscriptionPlan.Provider provider, @NonNull String providerPlanId) {
        val plan = SubscriptionPlan.builder()
            .provider(provider)
            .providerPlanId(providerPlanId)
            .billingPeriodMonths((short) 1)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(22500)
            .build();

        plan.setId((short) Math.round(Math.random() * 1000));
        return plan;
    }

    @NonNull
    private static Subscription buildSubscription(
        @NonNull AuthUser owner,
        @NonNull SubscriptionPlan plan,
        @NonNull Subscription.Status status
    ) {
        val subscription = Subscription.builder()
            .owner(owner)
            .plan(plan)
            .providerSubscriptionId(UUID.randomUUID().toString())
            .status(status)
            .startAt(status != Subscription.Status.CREATED ? LocalDateTime.now().minus(Duration.ofHours(1)) : null)
            .endAt(status == Subscription.Status.INACTIVE ? LocalDateTime.now().plus(Duration.ofHours(1)) : null)
            .build();

        subscription.setId(Math.round(Math.random() * 1000));
        return subscription;
    }
}
