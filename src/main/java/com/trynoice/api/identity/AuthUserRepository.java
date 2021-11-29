package com.trynoice.api.identity;

import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.platform.BasicEntityCrudRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link AuthUser} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface AuthUserRepository extends BasicEntityCrudRepository<AuthUser, Long> {

    /**
     * Finds an "active" auth user using their email.
     *
     * @param email a non-null search key for email column
     * @return a non-null {@link Optional}<{@link AuthUser}>.
     */
    @NonNull
    @Query("select e from AuthUser e where e.email = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<AuthUser> findActiveByEmail(@NonNull String email);
}
