package com.trynoice.api.identity.entities;

import com.trynoice.api.platform.BasicEntityRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface RefreshTokenRepository extends BasicEntityRepository<RefreshToken, Long> {

    /**
     * Marks all instances of the type {@link RefreshToken} for the given {@literal ownerId} as
     * deleted.
     *
     * @param ownerId must not be {@literal null}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update RefreshToken e set" + SET_INACTIVE_CLAUSE + "where e.owner.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteAllByOwnerId(@NonNull Long ownerId);

    /**
     * Marks expired refresh token entities as deleted in the database that expired before the
     * {@code expiredBefore} timestamp.
     *
     * @param expiredBefore a not {@literal null} timestamp to mark refresh token entities as
     *                      deleted that expired before this instant.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update RefreshToken e set" + SET_INACTIVE_CLAUSE + "where e.expiresAt < ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteAllExpired(@NonNull OffsetDateTime expiredBefore);
}
