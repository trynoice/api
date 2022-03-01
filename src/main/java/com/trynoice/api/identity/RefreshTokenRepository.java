package com.trynoice.api.identity;

import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.platform.BasicEntityRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}
