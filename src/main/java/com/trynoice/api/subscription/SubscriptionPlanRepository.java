package com.trynoice.api.subscription;

import com.trynoice.api.platform.BasicEntityCrudRepository;
import com.trynoice.api.subscription.models.SubscriptionPlan;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link SubscriptionPlan} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
interface SubscriptionPlanRepository extends BasicEntityCrudRepository<SubscriptionPlan, Short> {

    /**
     * Find all subscription plans offered by the given provider.
     *
     * @param provider must be a non-null {@link SubscriptionPlan.Provider}
     * @return a non-null list of available plans
     */
    @NonNull
    @Query("select e from SubscriptionPlan e where e.provider = ?1 and" + WHERE_ACTIVE_CLAUSE)
    List<SubscriptionPlan> findAllActiveByProvider(@NonNull SubscriptionPlan.Provider provider);
}
