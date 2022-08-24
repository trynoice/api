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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class AuthUserRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Test
    void findAllIdsDeactivatedBefore() {
        val deactivatedUsers = IntStream.range(0, 5)
            .mapToObj(i -> {
                val user = createAuthUser(entityManager);
                user.setDeactivatedAt(OffsetDateTime.now().minusHours(i));
                return entityManager.merge(user);
            })
            .collect(Collectors.toUnmodifiableList());

        val activeUsers = IntStream.range(0, 5)
            .mapToObj(i -> createAuthUser(entityManager))
            .collect(Collectors.toList());

        val expiredBefore = OffsetDateTime.now().minusHours(2);
        val ids = authUserRepository.findAllIdsDeactivatedBefore(expiredBefore);

        deactivatedUsers.stream()
            .filter(u -> u.getDeactivatedAt().isBefore(expiredBefore))
            .forEach(u -> assertTrue(ids.contains(u.getId())));

        deactivatedUsers.stream()
            .filter(u -> u.getDeactivatedAt().isAfter(expiredBefore))
            .forEach(u -> assertFalse(ids.contains(u.getId())));

        activeUsers.forEach(u -> assertFalse(ids.contains(u.getId())));
    }
}
