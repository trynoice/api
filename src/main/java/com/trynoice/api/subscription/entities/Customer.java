package com.trynoice.api.subscription.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * A data access object that maps to the {@code customer} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @NonNull
    @Id
    private Long userId;

    private String stripeId;
}
