package com.trynoice.api.subscription.entities;

import lombok.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link SubscriptionPlan}
 * entity.
 */
@Repository
public interface SubscriptionPlanRepository extends CrudRepository<SubscriptionPlan, Short>, PagingAndSortingRepository<SubscriptionPlan, Short> {

    @NonNull
    @Override
    default <S extends SubscriptionPlan> S save(@NonNull S entity) {
        throw new UnsupportedOperationException("subscription plan entities doesn't support updates");
    }

    @NonNull
    @Override
    default <S extends SubscriptionPlan> Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        throw new UnsupportedOperationException("subscription plan entities doesn't support updates");
    }

    /**
     * Find all subscription plans offered by the given provider.
     *
     * @param provider it must be a non-null {@link SubscriptionPlan.Provider}.
     * @param sort     not {@literal null} sort options for the query.
     * @return a non-null list of available plans.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from SubscriptionPlan e where e.provider = ?1")
    List<SubscriptionPlan> findAllByProvider(@NonNull SubscriptionPlan.Provider provider, @NonNull Sort sort);

    /**
     * Find a subscription plan by its provider plan id.
     *
     * @param provider   it must be a non-null {@link SubscriptionPlan.Provider}.
     * @param providedId plan id assigned by the provider. It must be a non-null string.
     * @return a non-empty optional if such a subscription plan exists in the repository.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from SubscriptionPlan e where e.provider = ?1 and e.providedId = ?2")
    Optional<SubscriptionPlan> findByProvidedId(@NonNull SubscriptionPlan.Provider provider, @NonNull String providedId);
}
