package com.trynoice.api.subscription.entities;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link Customer} entity.
 */
@Repository
public interface CustomerRepository extends CrudRepository<Customer, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Customer e set e.stripeId = null where e.stripeId = ?1")
    void resetStripeId(@NonNull String oldStripeId);
}
