package com.trynoice.api.subscription;

import com.trynoice.api.identity.AccountService;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionPlan;
import com.trynoice.api.subscription.viewmodels.SubscriptionPlanResponse;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AccountService} implements operations related to subscription management.
 */
@Service
class SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    SubscriptionService(@NonNull SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    /**
     * <p>
     * Fetch all available plans filtered by a {@code provider}.</p>
     *
     * <p>
     * If {@code provider} is {@code null}, it returns an unfiltered list of all available
     * subscription plans.</p>
     *
     * @param provider {@code null} or a valid {@link SubscriptionPlan.Provider}.
     * @return a non-null list of {@link SubscriptionPlanResponse}.
     * @throws UnsupportedSubscriptionPlanProviderException if an invalid {@code provider} is given.
     */
    @NonNull
    List<SubscriptionPlanResponse> getPlans(String provider) throws UnsupportedSubscriptionPlanProviderException {
        final List<SubscriptionPlan> plans;
        if (provider != null) {
            final SubscriptionPlan.Provider p;
            try {
                p = SubscriptionPlan.Provider.valueOf(provider);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedSubscriptionPlanProviderException(e);
            }

            plans = subscriptionPlanRepository.findAllActiveByProvider(p);
        } else {
            plans = subscriptionPlanRepository.findAllActive();
        }

        return plans.stream()
            .map(m -> SubscriptionPlanResponse.builder()
                .id(m.getId())
                .provider(m.getProvider().name())
                .providerPlanId(m.getProviderPlanId())
                .billingPeriodMonths(m.getBillingPeriodMonths())
                .priceInr(formatIndianPaiseToRupee(m.getPriceInIndianPaise()))
                .build())
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return a localised string after converting {@code paise} to INR
     */
    @NonNull
    private static String formatIndianPaiseToRupee(long paise) {
        val formatter = NumberFormat.getCurrencyInstance();
        formatter.setCurrency(Currency.getInstance("INR"));
        formatter.setMinimumFractionDigits(0);
        return formatter.format(paise / 100.0);
    }
}
