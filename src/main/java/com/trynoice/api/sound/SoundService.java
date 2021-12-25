package com.trynoice.api.sound;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.sound.exceptions.SegmentRequestAuthorizationException;
import com.trynoice.api.subscription.SubscriptionService;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@link SoundService} implements operations related to sound.
 */
@Service
class SoundService {

    private final LibraryManifestRepository libraryManifestRepository;
    private final SubscriptionService subscriptionService;

    @Autowired
    SoundService(@NonNull LibraryManifestRepository libraryManifestRepository, @NonNull SubscriptionService subscriptionService) {
        this.libraryManifestRepository = libraryManifestRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Authorizes a request for the given {@code segmentId} of the given {@code soundId}. If the
     * requested segment is premium, {@code principal} must be non-null, and must have an active
     * subscription. Throws {@link SegmentRequestAuthorizationException} if {@code principal} is not
     * authorized to access the requested segment.
     *
     * @param principal an {@code nullable} {@link AuthUser} that made this request.
     * @param soundId   the id of the sound being requested.
     * @param segmentId the id of the segment being requested.
     * @throws SegmentRequestAuthorizationException if the request should not be authorized.
     */
    void authorizeSegmentRequest(
        AuthUser principal,
        @NonNull String soundId,
        @NonNull String segmentId
    ) throws SegmentRequestAuthorizationException {
        val premiumSegmentMappings = libraryManifestRepository.getPremiumSegmentMappings();
        if (!premiumSegmentMappings.containsKey(soundId)) {
            throw new SegmentRequestAuthorizationException(String.format("sound with id '%s' doesn't exist", soundId));
        }

        // reversed search using startsWith and endsWith to cover bridge segments since they are not
        // explicitly present in the library manifest.
        val isRequestingFreeSegment = premiumSegmentMappings.get(soundId)
            .stream()
            .noneMatch(s -> segmentId.startsWith(s) || segmentId.endsWith(s));

        if (isRequestingFreeSegment) {
            return;
        }

        if (principal == null) {
            throw new SegmentRequestAuthorizationException("user is not signed-in");
        }

        if (!subscriptionService.isUserSubscribed(principal)) {
            throw new SegmentRequestAuthorizationException("user doesn't have an active subscription");
        }
    }
}
