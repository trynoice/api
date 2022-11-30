package com.trynoice.api.subscription.entities;

import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link Subscription} entity.
 */
@Repository
public interface SubscriptionRepository extends CrudRepository<Subscription, Long> {

    /**
     * Find a {@link Subscription} entity by its provider assigned subscription id.
     *
     * @param providedId it must be a non-null provider assigned subscription id.
     * @return an optional {@link Subscription} entity.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.providedId = ?1")
    Optional<Subscription> findByProvidedId(@NonNull String providedId);

    /**
     * Checks whether an active subscription ({@code startAt < now() < endAt}) exists with the given
     * {@code customerUserId}.
     *
     * @param customerUserId a not {@literal null} user id of the subscription owner.
     * @return whether the customer with given {@code customerUserId} has an active subscription.
     */
    @Transactional(readOnly = true)
    @Query("select case when count(e) > 0 then true else false end from Subscription e where " +
        "e.customer.userId = ?1 and e.startAt < now() and e.endAt > now()")
    boolean existsActiveByCustomerUserId(@NonNull Long customerUserId);

    /**
     * Retrieves all {@link Subscription} instances that belong to a given {@code customerUserId},
     * and were started ({@code startAt != null}) at some point.
     *
     * @param customerUserId a not {@literal null} user id of the subscription owner.
     * @param pageable       pagination options for the query.
     * @return a guaranteed to be not {@literal null} {@link List} of {@link Subscription} instances.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.customer.userId = ?1 and e.startAt <> null")
    Page<Subscription> findAllStartedByCustomerUserId(@NonNull Long customerUserId, @NonNull Pageable pageable);

    /**
     * Retrieves an {@link Optional} {@link Subscription} instance that is both active ({@code
     * startAt < now() < endAt}) and belongs to a given {@code customerUserId}.
     *
     * @param customerUserId a not {@literal null} user id of the subscription owner.
     * @return a not {@literal null} {@link Optional} of the {@link Subscription} instance.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from Subscription e where e.customer.userId = ?1 and e.startAt < now() and e.endAt > now()")
    Optional<Subscription> findActiveByCustomerUserId(@NonNull Long customerUserId);

    /**
     * Deletes all entities from the database that have {@literal null} {@link
     * Subscription#getProvidedId()}, {@link Subscription#getStartAt()} and {@link
     * Subscription#getEndAt()}, and were created before the given {@code createdBefore} timestamp.
     *
     * @param createdBefore a not {@literal null} timestamp to remove incomplete entities that were
     *                      created before this instant.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from Subscription e where e.providedId is null and e.startAt is null and e.endAt is null and e.createdAt < ?1")
    void deleteAllIncompleteCreatedBefore(@NonNull OffsetDateTime createdBefore);
}
