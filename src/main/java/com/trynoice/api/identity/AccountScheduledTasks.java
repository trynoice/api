package com.trynoice.api.identity;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collection of scheduled tasks for {@link AccountService}.
 */
@Component
@Slf4j
class AccountScheduledTasks {

    private final AccountService accountService;

    @Autowired
    AccountScheduledTasks(@NonNull AccountService accountService) {
        this.accountService = accountService;
    }

    @Scheduled(cron = "${app.auth.garbage-collection-schedule}")
    void garbageCollection() {
        log.info("performing garbage collection");
        accountService.performGarbageCollection();
    }
}
