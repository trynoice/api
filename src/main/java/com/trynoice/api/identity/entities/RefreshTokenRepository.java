package com.trynoice.api.identity.entities;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} entity.
 */
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    /**
     * Deletes all instances of the type {@link RefreshToken} for the given {@literal ownerId}.
     *
     * @param ownerId must not be {@literal null}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from RefreshToken e where e.owner.id = ?1")
    void deleteAllByOwnerId(@NonNull Long ownerId);
}
