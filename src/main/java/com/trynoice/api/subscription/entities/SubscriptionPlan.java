package com.trynoice.api.subscription.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * A data access object that maps to the {@code subscription_plan} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private short id;

    @NonNull
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @NonNull
    private String providedId;

    private short billingPeriodMonths;

    private short trialPeriodDays;

    private int priceInIndianPaise;

    public enum Provider {
        GOOGLE_PLAY,
        STRIPE,
        GIFT_CARD,
    }
}
