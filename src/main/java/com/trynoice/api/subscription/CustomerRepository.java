package com.trynoice.api.subscription;

import com.trynoice.api.subscription.entities.Customer;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link Customer} entity.
 */
@Repository
public interface CustomerRepository extends CrudRepository<Customer, Long> {

    /**
     * @param userId a not {@literal null} user id corresponding to a customer
     * @return a guaranteed not {@literal null} {@link Optional}, with the customer's stripe id if
     * present.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e.stripeId from Customer e where e.id = ?1")
    Optional<String> findStripeIdByUserId(@NonNull Long userId);
}
