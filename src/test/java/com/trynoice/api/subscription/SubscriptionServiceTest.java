package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.identity.AuthUserRepository;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionConfiguration;
import com.trynoice.api.subscription.models.SubscriptionPlan;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

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
            androidPublisherApi);
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
    void handleGooglePlayWebhookEvent() {
        // The unit test quickly becomes too complex due to all the mocking. The best case scenario
        // here is to try covering as many cases in integration tests as possible.
    }
}