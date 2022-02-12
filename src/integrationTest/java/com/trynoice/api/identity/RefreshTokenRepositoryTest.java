package com.trynoice.api.identity;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
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
    void deleteAllByOwnerId() {
        val user = createAuthUser(entityManager);

        val ownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, user))
            .collect(Collectors.toUnmodifiableList());

        val unownedRefreshTokens = IntStream.range(0, 5)
            .mapToObj(i -> createRefreshToken(entityManager, createAuthUser(entityManager)))
            .collect(Collectors.toUnmodifiableList());

        refreshTokenRepository.deleteAllByOwnerId(user.getId());
        assertTrue(
            ownedRefreshTokens.stream()
                .noneMatch(t -> refreshTokenRepository.existsById(t.getId())));

        assertTrue(
            unownedRefreshTokens.stream()
                .allMatch(t -> refreshTokenRepository.existsById(t.getId())));
    }
}
