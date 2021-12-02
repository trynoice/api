package com.trynoice.api.subscription.models;

import com.trynoice.api.platform.BasicEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * A data access object that maps to the {@code subscription_plan} table in the database.
 */
@Entity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubscriptionPlan extends BasicEntity<Short> {

    @Enumerated(EnumType.STRING)
    @NonNull
    private Provider provider;

    @NonNull
    private String providerPlanId;

    @NonNull
    private Short billingPeriodMonths;

    @NonNull
    private Integer priceInIndianPaise;

    public enum Provider {
        GOOGLE_PLAY,
        RAZORPAY
    }
}
