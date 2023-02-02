package com.trynoice.api.subscription.entities;

import jakarta.persistence.EntityManager;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class SubscriptionRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void deleteAllIncompleteCreatedBefore() {
        val plan = entityManager.merge(
            SubscriptionPlan.builder()
                .provider(SubscriptionPlan.Provider.STRIPE)
                .providedId("test-provided-id")
                .billingPeriodMonths((short) 1)
                .priceInIndianPaise(100)
                .trialPeriodDays((short) 0)
                .build());

        val customer = entityManager.merge(
            Customer.builder()
                .userId(1)
                .build());

        val now = OffsetDateTime.now();
        val stale = now.minusDays(1);

        val completed = IntStream.range(0, 5)
            .mapToObj(i -> subscriptionRepository.save(
                Subscription.builder()
                    .createdAt(i % 2 == 0 ? now : stale)
                    .customer(customer)
                    .plan(plan)
                    .providedId("test-provided-id-" + i)
                    .startAt(OffsetDateTime.now())
                    .endAt(OffsetDateTime.now().minusHours(1))
                    .isAutoRenewing(i % 2 == 0)
                    .build()))
            .toList();

        val incomplete = IntStream.range(5, 10)
            .mapToObj(i -> subscriptionRepository.save(
                Subscription.builder()
                    .createdAt(i % 2 == 0 ? stale : now)
                    .customer(customer)
                    .plan(plan)
                    .providedId(null)
                    .startAt(null)
                    .endAt(null)
                    .isAutoRenewing(true)
                    .build()))
            .toList();

        subscriptionRepository.deleteAllIncompleteCreatedBefore(stale.plusMinutes(1));

        completed.forEach(s -> assertTrue(subscriptionRepository.existsById(s.getId())));
        incomplete.forEach(s -> assertEquals(s.getCreatedAt().isEqual(stale), !subscriptionRepository.existsById(s.getId())));
    }
}
