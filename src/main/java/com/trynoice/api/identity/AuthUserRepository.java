package com.trynoice.api.identity;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.platform.BasicEntityCrudRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link AuthUser} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
interface AuthUserRepository extends BasicEntityCrudRepository<AuthUser, Long> {

    /**
     * Finds an auth user using their email.
     *
     * @param email a non-null search key for email column
     * @return a non-null {@link Optional}<{@link AuthUser}>.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from AuthUser e where e.email = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<AuthUser> findByEmail(@NonNull String email);

    @NonNull
    @Transactional(readOnly = true)
    @Query("select e.email from AuthUser e where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<String> findEmailById(@NonNull Long id);
}
