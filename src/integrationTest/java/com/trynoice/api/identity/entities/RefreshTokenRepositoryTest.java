package com.trynoice.api.identity.entities;

import jakarta.persistence.EntityManager;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
            .toList();

        val unownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, createAuthUser(entityManager)))
            .toList();

        refreshTokenRepository.updateExpiresAtOfAllByOwnerId(OffsetDateTime.now(), user.getId());
        ownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(t -> assertTrue(t.getExpiresAt().isBefore(OffsetDateTime.now())));

        unownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(t -> assertTrue(t.getExpiresAt().isAfter(OffsetDateTime.now())));
    }

    @Test
    void deleteAllByOwnerId() {
        val user = createAuthUser(entityManager);
        val ownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, user))
            .toList();

        val unownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, createAuthUser(entityManager)))
            .toList();

        refreshTokenRepository.deleteAllByOwnerId(user.getId());
        ownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(Assertions::assertNull);

        unownedRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(Assertions::assertNotNull);
    }

    @Test
    void deleteAllExpiredBefore() {
        val expiredRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> {
                val token = createRefreshToken(entityManager, createAuthUser(entityManager));
                token.setExpiresAt(OffsetDateTime.now().minusHours(i));
                return entityManager.merge(token);
            })
            .toList();

        val activeRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, createAuthUser(entityManager)))
            .toList();

        val deleteBefore = OffsetDateTime.now().minusHours(2);
        refreshTokenRepository.deleteAllExpiredBefore(deleteBefore);
        expiredRefreshTokens.stream()
            .filter(t -> t.getExpiresAt().isBefore(deleteBefore))
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(Assertions::assertNull);

        expiredRefreshTokens.stream()
            .filter(t -> t.getExpiresAt().isAfter(deleteBefore))
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(Assertions::assertNotNull);

        activeRefreshTokens.stream()
            .map(t -> entityManager.find(RefreshToken.class, t.getId()))
            .forEach(Assertions::assertNotNull);
    }
}
