package com.trynoice.api.subscription.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;

/**
 * A data access object that maps to the {@code customer} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    private long userId;

    @Version
    private long version;

    private String stripeId;

    private boolean isTrialPeriodUsed;
}
