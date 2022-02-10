package com.trynoice.api.identity;

import com.trynoice.api.identity.entities.RefreshToken;
import com.trynoice.api.platform.BasicEntityCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface RefreshTokenRepository extends BasicEntityCrudRepository<RefreshToken, Long> {
}
