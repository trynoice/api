package com.trynoice.api.subscription;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collection of scheduled tasks for {@link SubscriptionService}.
 */
@Component
@Slf4j
class SubscriptionScheduledTasks {

    private final SubscriptionService subscriptionService;

    @Autowired
    SubscriptionScheduledTasks(@NonNull SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "${app.subscriptions.garbage-collection-schedule}")
    void garbageCollection() {
        log.info("performing garbage collection");
        subscriptionService.performGarbageCollection();
    }

    @Scheduled(fixedRateString = "${app.subscriptions.foreign-exchange-rate-refresh-interval-millis}")
    void updateForeignExchangeRates() {
        log.info("updating foreign exchange rates");
        subscriptionService.updateForeignExchangeRates();
    }
}
