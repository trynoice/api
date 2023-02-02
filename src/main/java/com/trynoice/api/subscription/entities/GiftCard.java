package com.trynoice.api.subscription.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

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
