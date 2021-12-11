package com.trynoice.api.subscription;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.platform.BasicEntityCrudRepository;
import com.trynoice.api.subscription.entities.Subscription;
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
     * @return an optional {@link Subscription} entity.
     */
    @NonNull
    @Query("select e from Subscription e where e.providerSubscriptionId = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<Subscription> findActiveByProviderSubscriptionId(@NonNull String providerSubscriptionId);

    /**
     * Find a {@link Subscription} entity by its owner and {@link Subscription.Status}.
     *
     * @param owner    owner of the subscription.
     * @param statuses expected status of the subscription
     * @return an optional {@link Subscription} entity.
     */
    @NonNull
    @Query("select e from Subscription e where e.owner = ?1 and e.status in ?2 and" + WHERE_ACTIVE_CLAUSE)
    Optional<Subscription> findActiveByOwnerAndStatus(@NonNull AuthUser owner, @NonNull Subscription.Status... statuses);
}
