package com.trynoice.api.subscription;

import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
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
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionPlan googlePlayPlan, razorpayPlan;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        lenient().when(googlePlayPlan.getId()).thenReturn((short) 1);
        lenient().when(googlePlayPlan.getProvider()).thenReturn(SubscriptionPlan.Provider.GOOGLE_PLAY);
        lenient().when(googlePlayPlan.getProviderPlanId()).thenReturn("google_play_plan_id");
        lenient().when(googlePlayPlan.getBillingPeriodMonths()).thenReturn((short) 1);
        lenient().when(googlePlayPlan.getPriceInIndianPaise()).thenReturn(10000);

        lenient().when(razorpayPlan.getId()).thenReturn((short) 2);
        lenient().when(razorpayPlan.getProvider()).thenReturn(SubscriptionPlan.Provider.RAZORPAY);
        lenient().when(razorpayPlan.getProviderPlanId()).thenReturn("razorpay_plan_id");
        lenient().when(razorpayPlan.getBillingPeriodMonths()).thenReturn((short) 1);
        lenient().when(razorpayPlan.getPriceInIndianPaise()).thenReturn(10000);

        lenient()
            .when(subscriptionPlanRepository.findAllActive())
            .thenReturn(List.of(googlePlayPlan, razorpayPlan));

        lenient()
            .when(subscriptionPlanRepository.findAllActiveByProvider(SubscriptionPlan.Provider.GOOGLE_PLAY))
            .thenReturn(List.of(googlePlayPlan));

        lenient()
            .when(subscriptionPlanRepository.findAllActiveByProvider(SubscriptionPlan.Provider.RAZORPAY))
            .thenReturn(List.of(razorpayPlan));

        service = new SubscriptionService(subscriptionPlanRepository);
    }

    @Test
    void getPlans_withSupportedProviders() throws UnsupportedSubscriptionPlanProviderException {
        val testCases = new HashMap<SubscriptionPlan.Provider, List<SubscriptionPlan>>();
        testCases.put(null, List.of(googlePlayPlan, razorpayPlan));
        testCases.put(SubscriptionPlan.Provider.GOOGLE_PLAY, List.of(googlePlayPlan));
        testCases.put(SubscriptionPlan.Provider.RAZORPAY, List.of(razorpayPlan));

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
}
