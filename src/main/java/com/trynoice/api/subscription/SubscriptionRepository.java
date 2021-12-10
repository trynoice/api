package com.trynoice.api.subscription;

import com.trynoice.api.platform.BasicEntityCrudRepository;
import com.trynoice.api.subscription.models.Subscription;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link Subscription} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
interface SubscriptionRepository extends BasicEntityCrudRepository<Subscription, Long> {

    /**
     * Find a {@link Subscription} entity by its provider assigned subscription id.
     *
     * @param providerSubscriptionId it must be a non-null provider assigned subscription id.
     * @return an optional {@link Subscription}.
     */
    @NonNull
    @Query("select e from Subscription e where e.providerSubscriptionId = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<Subscription> findActiveByProviderSubscriptionId(@NonNull String providerSubscriptionId);
}
