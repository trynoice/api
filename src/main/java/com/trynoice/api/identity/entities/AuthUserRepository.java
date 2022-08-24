package com.trynoice.api.identity.entities;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link AuthUser} entity.
 */
@Repository
public interface AuthUserRepository extends CrudRepository<AuthUser, Long> {

    /**
     * Retrieves a user by its email.
     *
     * @param email must not be {@literal null}.
     * @return the user with the given email or {@literal Optional#empty()} if none found.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from AuthUser e where e.email = ?1")
    Optional<AuthUser> findByEmail(@NonNull String email);

    /**
     * Retrieves a user's email by its id.
     *
     * @param id must not be {@literal null}.
     * @return the email for the given user id or {@literal Optional#empty()} if none found.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e.email from AuthUser e where e.id = ?1")
    Optional<String> findEmailById(@NonNull Long id);

    /**
     * Returns whether a user with the given email exists.
     *
     * @param email must not be {@literal null}.
     * @return {@literal true} if a user with the given email exists, {@literal false} otherwise.
     */
    @Transactional(readOnly = true)
    @Query("select case when count(e) > 0 then true else false end from AuthUser e where e.email = ?1")
    boolean existsByEmail(@NonNull String email);

    /**
     * Returns ids of the users that deactivated their account before the given {@code before}
     * timestamp.
     *
     * @param before a not {@literal null} timestamp.
     * @return a not {@literal null} list of ids.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e.id from AuthUser e where e.deactivatedAt < ?1")
    List<Long> findAllIdsDeactivatedBefore(@NonNull OffsetDateTime before);
}
