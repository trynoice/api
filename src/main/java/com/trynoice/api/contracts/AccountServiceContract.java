package com.trynoice.api.contracts;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.Optional;

/**
 * Defines a service contract for account service to provide certain operations to subscription
 * service.
 */
public interface AccountServiceContract {

    /**
     * @param userId id of a user.
     * @return a non-null {@link Optional}<{@link String}>, with the email address corresponding to
     * the given {@code userId} if found.
     */
    @NonNull
    Optional<String> findEmailByUser(@NonNull Long userId);

    /**
     * An event that the account service publishes when it permanently removes a user.
     */
    @Getter
    @AllArgsConstructor
    final class UserDeletedEvent {
        private long userId;
    }
}
