package com.trynoice.api.subscription.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Version;
import java.time.OffsetDateTime;

/**
 * A data access object that maps to the {@code gift_card} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCard {

    @Id
    private long id;

    @NonNull
    @Column(updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Version
    private long version;

    @NonNull
    private String code;

    private int hourCredits;

    @NonNull
    @ManyToOne
    private SubscriptionPlan plan;

    @ManyToOne
    private Customer customer;

    private boolean isRedeemed;

    private OffsetDateTime expiresAt;
}
