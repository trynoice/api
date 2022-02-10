package com.trynoice.api.subscription;

import com.trynoice.api.platform.BasicEntityCrudRepository;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link SubscriptionPlan} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
interface SubscriptionPlanRepository extends BasicEntityCrudRepository<SubscriptionPlan, Short> {

    /**
     * Find all subscription plans offered by the given provider.
     *
     * @param provider it must be a non-null {@link SubscriptionPlan.Provider}.
     * @return a non-null list of available plans.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from SubscriptionPlan e where e.provider = ?1 and" + WHERE_ACTIVE_CLAUSE)
    List<SubscriptionPlan> findAllByProvider(@NonNull SubscriptionPlan.Provider provider);

    /**
     * Find a subscription plan by its provider plan id.
     *
     * @param provider it must be a non-null {@link SubscriptionPlan.Provider}.
     * @param planId   plan id assigned by the provider. It must be a non-null string.
     * @return a non-empty optional if such a subscription plan exists in the repository.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from SubscriptionPlan e where e.provider = ?1 and e.providerPlanId = ?2 and" + WHERE_ACTIVE_CLAUSE)
    Optional<SubscriptionPlan> findByProviderPlanId(@NonNull SubscriptionPlan.Provider provider, @NonNull String planId);
}
