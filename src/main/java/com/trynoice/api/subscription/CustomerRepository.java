package com.trynoice.api.subscription;

import com.trynoice.api.subscription.entities.Customer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link Customer} entity.
 */
@Repository
public interface CustomerRepository extends CrudRepository<Customer, Long> {
}
