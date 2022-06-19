package com.trynoice.api.subscription.entities;

import com.trynoice.api.platform.BasicEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.OffsetDateTime;

/**
 * A data access object that maps to the {@code gift_card} table in the database.
 */
@Entity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GiftCard extends BasicEntity {

    @Id
    private long id;

    @NonNull
    private String code;

    private short hourCredits;

    @NonNull
    @ManyToOne
    private SubscriptionPlan plan;

    @ManyToOne
    private Customer customer;

    private boolean isRedeemed;

    private OffsetDateTime expiresAt;
}
