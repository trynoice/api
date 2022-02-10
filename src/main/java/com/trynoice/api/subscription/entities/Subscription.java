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
import javax.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    private Long ownerId;

    @NonNull
    @ManyToOne(optional = false)
    private SubscriptionPlan plan;

    private String providerSubscriptionId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.CREATED;

    private LocalDateTime startAt, endAt;

    private String stripeCustomerId;

    /**
     * Helper to set {@link Subscription#startAt} using Epoch seconds.
     */
    public void setStartAtSeconds(long seconds) {
        this.startAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#startAt} using Epoch milliseconds.
     */
    public void setStartAtMillis(long millis) {
        this.startAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#endAt} using Epoch seconds.
     */
    public void setEndAtSeconds(long seconds) {
        this.endAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#endAt} using Epoch milliseconds.
     */
    public void setEndAtMillis(long millis) {
        this.endAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

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
