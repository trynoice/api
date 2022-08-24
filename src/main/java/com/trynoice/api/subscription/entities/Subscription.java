package com.trynoice.api.subscription.entities;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.val;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A data access object that maps to the {@code subscription} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    @Column(updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Version
    private long version;

    @NonNull
    @ManyToOne(optional = false)
    private Customer customer;

    @NonNull
    @ManyToOne(optional = false)
    private SubscriptionPlan plan;

    private String providedId;

    private boolean isPaymentPending;

    @Builder.Default
    private boolean isAutoRenewing = true;

    private boolean isRefunded;

    private OffsetDateTime startAt, endAt;

    /**
     * Helper to set {@link Subscription#startAt} using Epoch seconds.
     */
    public void setStartAtSeconds(long seconds) {
        this.startAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#startAt} using Epoch milliseconds.
     */
    public void setStartAtMillis(long millis) {
        this.startAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#endAt} using Epoch seconds.
     */
    public void setEndAtSeconds(long seconds) {
        this.endAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    /**
     * Helper to set {@link Subscription#endAt} using Epoch milliseconds.
     */
    public void setEndAtMillis(long millis) {
        this.endAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public boolean isActive() {
        val now = OffsetDateTime.now();
        return startAt != null && startAt.isBefore(now) && endAt != null && endAt.isAfter(now);
    }
}
