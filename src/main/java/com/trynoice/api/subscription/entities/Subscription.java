package com.trynoice.api.subscription.entities;


import com.trynoice.api.identity.entities.AuthUser;
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

    private String providerSubscriptionId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.CREATED;

    private LocalDateTime startAt, endAt;

    /**
     * Indicates the current subscription status.
     */
    public enum Status {
        /**
         * Subscription has yet to be activated, but the user has initiated the subscription flow.
         * The user shouldn't be granted access to its entitlements, unless subscription transitions
         * to {@link Status#PENDING PENDING} or {@link Status#ACTIVE ACTIVE} status.
         */
        CREATED,

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
