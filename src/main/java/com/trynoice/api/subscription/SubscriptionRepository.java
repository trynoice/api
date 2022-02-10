package com.trynoice.api.subscription;

import com.trynoice.api.platform.BasicEntityCrudRepository;
import com.trynoice.api.subscription.entities.Subscription;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.providerSubscriptionId = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<Subscription> findActiveByProviderSubscriptionId(@NonNull String providerSubscriptionId);

    /**
     * Find a {@link Subscription} entity by its owner and {@link Subscription.Status}.
     *
     * @param ownerId  id of the subscription owner.
     * @param statuses expected status of the subscription
     * @return an optional {@link Subscription} entity.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.ownerId = ?1 and e.status in ?2 and" + WHERE_ACTIVE_CLAUSE)
    Optional<Subscription> findActiveByOwnerAndStatus(@NonNull Long ownerId, @NonNull Subscription.Status... statuses);

    /**
     * Find all {@link Subscription} entities by its owner and {@link Subscription.Status}.
     *
     * @param ownerId  id of the subscription owner.
     * @param statuses expected status of the subscription
     * @return a list of {@link Subscription} entities.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.ownerId = ?1 and e.status in ?2 and" + WHERE_ACTIVE_CLAUSE)
    List<Subscription> findAllActiveByOwnerAndStatus(@NonNull Long ownerId, @NonNull Subscription.Status... statuses);
}
