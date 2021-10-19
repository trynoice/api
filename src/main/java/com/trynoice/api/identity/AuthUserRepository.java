package com.trynoice.api.identity;

import com.trynoice.api.data.BasicEntityCRUDRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthUserRepository extends BasicEntityCRUDRepository<AuthUser, Integer> {
}
