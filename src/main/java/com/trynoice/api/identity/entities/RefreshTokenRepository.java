package com.trynoice.api.identity.entities;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} entity.
 */
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    /**
     * Marks all instances of the type {@link RefreshToken} as expired for the given {@literal
     * ownerId}.
     *
     * @param ownerId must not be {@literal null}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update RefreshToken e set e.expiresAt = ?1 where e.owner.id = ?2")
    void updateExpiresAtOfAllByOwnerId(@NonNull OffsetDateTime expiresAt, @NonNull Long ownerId);

    /**
     * Deletes any refresh token entities owned by the given {@code ownerId}.
     *
     * @param ownerId must not be {@literal null}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from RefreshToken e where e.owner.id = ?1")
    void deleteAllByOwnerId(@NonNull Long ownerId);

    /**
     * Deletes any expired refresh token entities from the database that expired before the given
     * {@code before} timestamp.
     *
     * @param before a not {@literal null} timestamp to remove entities that expired before this
     *               instant.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from RefreshToken e where e.expiresAt < ?1")
    void deleteAllExpiredBefore(@NonNull OffsetDateTime before);
}
