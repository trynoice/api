package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.AuthUserRepository;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.CreateSubscriptionParams;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
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
    private AuthUserRepository authUserRepository;

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
            authUserRepository,
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
        when(subscriptionRepository.findActiveByOwnerAndStatus(eq(authUser), any()))
            .thenReturn(Optional.of(mock(Subscription.class)));
        when(subscriptionPlanRepository.findActiveById((short) 1))
            .thenReturn(Optional.of(buildStripeSubscriptionPlan("provider-plan-id")));

        val params = new CreateSubscriptionParams((short) 1, "success-url", "cancel-url");
        assertThrows(DuplicateSubscriptionException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withInvalidPlanId() {
        val authUser = buildAuthUser();
        val planId = (short) 1;
        val params = new CreateSubscriptionParams(planId, "success-url", "cancel-url");
        when(subscriptionPlanRepository.findActiveById(planId)).thenReturn(Optional.empty());
        assertThrows(SubscriptionPlanNotFoundException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withStripeApiError() throws Exception {
        val authUser = buildAuthUser();
        val stripePriceId = "stripe-price-id-1";
        val planId = (short) 1;
        val params = new CreateSubscriptionParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(buildStripeSubscriptionPlan(stripePriceId)));

        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any()))
            .thenThrow(new ApiConnectionException("test-error"));

        assertThrows(StripeException.class, () -> service.createSubscription(authUser, params));
    }

    @Test
    void createSubscription_withValidParams() throws Exception {
        val authUser = buildAuthUser();
        val stripePriceId = "stripe-price-id-2";
        val planId = (short) 1;
        val params = new CreateSubscriptionParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findActiveById(planId))
            .thenReturn(Optional.of(buildStripeSubscriptionPlan(stripePriceId)));

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

        assertEquals(redirectUrl, service.createSubscription(authUser, params));
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
}
