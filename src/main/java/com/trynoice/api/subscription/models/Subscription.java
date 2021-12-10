package com.trynoice.api.subscription.models;


import com.trynoice.api.identity.models.AuthUser;
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
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

/**
 * A data access object that maps to the {@code subscription} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Subscription extends BasicEntity<Long> {

    @NonNull
    @ManyToOne(optional = false)
    private AuthUser owner;

    @NonNull
    @ManyToOne(optional = false)
    private SubscriptionPlan plan;

    @NonNull
    private String providerSubscriptionId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @NonNull
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /**
     * Indicates the current subscription status.
     */
    public enum Status {
        /**
         * Subscription payment is pending or delayed, but the user still has access to its
         * entitlements.
         */
        PENDING,

        /**
         * Subscription is active and user has access to its entitlements.
         */
        ACTIVE,

        /**
         * Subscription has ended, expired or on hold, and user has lost access to its entitlements.
         */
        INACTIVE
    }
}