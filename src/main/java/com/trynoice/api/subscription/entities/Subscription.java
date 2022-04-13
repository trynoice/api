package com.trynoice.api.subscription.entities;


import com.trynoice.api.platform.BasicEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.val;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
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
@EqualsAndHashCode(callSuper = true)
public class Subscription extends BasicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    @ManyToOne(optional = false)
    private Customer customer;

    @NonNull
    @ManyToOne(optional = false)
    private SubscriptionPlan plan;

    private String providerSubscriptionId;

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
