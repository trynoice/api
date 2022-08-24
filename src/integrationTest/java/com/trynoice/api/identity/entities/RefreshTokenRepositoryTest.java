package com.trynoice.api.identity.entities;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static com.trynoice.api.testing.AuthTestUtils.createRefreshToken;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class RefreshTokenRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void updateExpiresAtOfAllByOwnerId() {
        val user = createAuthUser(entityManager);

        val ownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, user))
            .collect(Collectors.toUnmodifiableList());

        val unownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, createAuthUser(entityManager)))
            .collect(Collectors.toUnmodifiableList());

        refreshTokenRepository.updateExpiresAtOfAllByOwnerId(OffsetDateTime.now(), user.getId());
        ownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(t -> assertTrue(t.getExpiresAt().isBefore(OffsetDateTime.now())));

        unownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(t -> assertTrue(t.getExpiresAt().isAfter(OffsetDateTime.now())));
    }
}
