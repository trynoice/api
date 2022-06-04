package com.trynoice.api.subscription.entities;

import com.trynoice.api.platform.BasicEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * A data access object that maps to the {@code subscription_plan} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubscriptionPlan extends BasicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private short id;

    @NonNull
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @NonNull
    private String providerPlanId;

    private short billingPeriodMonths;

    private short trialPeriodDays;

    private int priceInIndianPaise;

    public enum Provider {
        GOOGLE_PLAY,
        STRIPE,
        GIFT_CARD,
    }
}
