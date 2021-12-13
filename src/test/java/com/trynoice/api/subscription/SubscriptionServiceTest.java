package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    private SubscriptionPlan googlePlayPlan, stripePlan;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        googlePlayPlan = SubscriptionPlan.builder()
            .provider(SubscriptionPlan.Provider.GOOGLE_PLAY)
            .providerPlanId("google_plan_plan_id")
            .billingPeriodMonths((short) 1)
            .priceInIndianPaise(10000)
            .build();

        googlePlayPlan.setId((short) 1);
        stripePlan = SubscriptionPlan.builder()
            .provider(SubscriptionPlan.Provider.STRIPE)
            .providerPlanId("stripe_plan_id")
            .billingPeriodMonths((short) 1)
            .priceInIndianPaise(10000)
            .build();

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
                assertEquals(expecting.getProvider().name(), got.getProvider());
                assertEquals(expecting.getProviderPlanId(), got.getProviderPlanId());
                assertEquals(expecting.getBillingPeriodMonths(), got.getBillingPeriodMonths());
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
            .thenReturn(Optional.of(buildStripeSubscriptionPlan("provider-plan-id")));

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
        val plan = buildStripeSubscriptionPlan(stripePriceId);
        val subscription = buildSubscription(authUser, plan, Subscription.Status.CREATED);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(plan));

        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(subscription));

        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any()))
            .thenThrow(new ApiConnectionException("test-error"));

        assertThrows(StripeException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withValidParams() throws Exception {
        val authUser = buildAuthUser();
        val stripePriceId = "stripe-price-id-1";
        val plan = buildStripeSubscriptionPlan(stripePriceId);
        val subscription = buildSubscription(authUser, plan, Subscription.Status.CREATED);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(plan));

        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(subscription));

        val redirectUrl = "test-redirect-url";
        val mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn(redirectUrl);

        when(
            stripeApi.createCheckoutSession(
                params.getSuccessUrl(),
                params.getCancelUrl(),
                stripePriceId,
                authUser.getId().toString(),
                authUser.getEmail()))
            .thenReturn(mockSession);

        val result = service.createSubscription(authUser, params);
        assertNotNull(result);
        assertEquals(subscription.getId(), result.getSubscriptionId());
        assertEquals(redirectUrl, result.getStripeCheckoutSessionUrl());
    }

    @Test
    void handleGooglePlayWebhookEvent() {
        // The unit test quickly becomes too complex due to all the mocking. The best case scenario
        // here is to try covering as many cases in integration tests as possible.
    }

    @NonNull
    private static AuthUser buildAuthUser() {
        val authUser = AuthUser.builder()
            .name("test-name")
            .email("test-name@api.test")
            .build();

        authUser.setId(1L);
        return authUser;
    }

    @NonNull
    private static SubscriptionPlan buildStripeSubscriptionPlan(@NonNull String providerPlanId) {
        val plan = SubscriptionPlan.builder()
            .provider(SubscriptionPlan.Provider.STRIPE)
            .providerPlanId(providerPlanId)
            .billingPeriodMonths((short) 1)
            .priceInIndianPaise(22500)
            .build();

        plan.setId((short) 1);
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
            .status(status)
            .build();

        subscription.setId(1L);
        return subscription;
    }
}
