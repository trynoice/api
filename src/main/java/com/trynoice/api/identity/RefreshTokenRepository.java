package com.trynoice.api.identity;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.platform.BasicEntityCrudRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface RefreshTokenRepository extends BasicEntityCrudRepository<RefreshToken, Long> {

    /**
     * @param owner to filter the returned list.
     * @return a list of {@link RefreshToken} entities owned by the provided {@link AuthUser owner}.
     */
    @NonNull
    @Query("select e from RefreshToken e where e.owner = ?1 and" + WHERE_ACTIVE_CLAUSE)
    List<RefreshToken> findAllActiveByOwner(@NonNull AuthUser owner);
}
