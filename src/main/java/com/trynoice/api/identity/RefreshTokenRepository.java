package com.trynoice.api.identity;

import com.trynoice.api.identity.models.RefreshToken;
import com.trynoice.api.platform.BasicEntityCRUDRepository;
import org.springframework.stereotype.Repository;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link RefreshToken} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface RefreshTokenRepository extends BasicEntityCRUDRepository<RefreshToken, Long> {
}
