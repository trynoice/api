package com.trynoice.api.subscription.entities;

import com.trynoice.api.platform.BasicEntityRepository;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A JPA {@link Repository} declaration for database interactions of {@link GiftCard} {@link
 * com.trynoice.api.platform.BasicEntity BasicEntity}.
 */
@Repository
public interface GiftCardRepository extends BasicEntityRepository<GiftCard, Long> {

    /**
     * Retrieves a gift card by its {@code code}.
     *
     * @param code must not be {@literal null}.
     * @return the gift card corresponding to the given {@code code} or {@literal Optional#empty()}.
     */
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from GiftCard e where e.code = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<GiftCard> findByCode(@NonNull String code);
}
