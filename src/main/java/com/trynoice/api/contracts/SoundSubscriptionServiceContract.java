package com.trynoice.api.contracts;

import lombok.NonNull;

/**
 * Defines a service contract for subscription service to provide certain operations to sound
 * service.
 */
public interface SoundSubscriptionServiceContract {

    /**
     * @param userId id of a user.
     * @return {@code true} if the user with given {@code userId} owns a valid subscription,
     * {@code false} otherwise.
     */
    boolean isUserSubscribed(@NonNull Long userId);
}
