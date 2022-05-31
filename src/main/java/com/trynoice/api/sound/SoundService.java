package com.trynoice.api.sound;

import com.trynoice.api.contracts.SubscriptionServiceContract;
import com.trynoice.api.sound.exceptions.SegmentAccessDeniedException;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.util.Set;

/**
 * {@link SoundService} implements operations related to sound.
 */
@Service
class SoundService {

    private final LibraryManifestRepository libraryManifestRepository;
    private final Set<String> freeAudioBitrates;
    private final SubscriptionServiceContract subscriptionServiceContract;

    @Autowired
    SoundService(
        @NonNull SoundConfiguration soundConfig,
        @NonNull LibraryManifestRepository libraryManifestRepository,
        @NonNull SubscriptionServiceContract subscriptionServiceContract
    ) {
        this.libraryManifestRepository = libraryManifestRepository;
        this.freeAudioBitrates = soundConfig.getFreeBitrates();
        this.subscriptionServiceContract = subscriptionServiceContract;
    }

    /**
     * Authorizes a request for the given {@code segmentId} of the given {@code soundId}. If the
     * requested segment is premium, {@code principalId} must be non-null, and the user with the
     * given {@code principalId} must have an active subscription. Throws {@link
     * SegmentAccessDeniedException} if {@code principalId} is not authorized to access the
     * requested segment.
     *
     * @param principalId an {@code nullable} id of the user that made this request.
     * @param soundId     the id of the sound being requested.
     * @param segmentId   the id of the segment being requested.
     * @throws SegmentAccessDeniedException if {@code principalId} requests a premium segment but
     *                                      isn't signed-in ({@code null}) or doesn't have an active
     *                                      subscription.
     */
    void authorizeSegmentRequest(
        Long principalId,
        @NonNull String soundId,
        @NonNull String segmentId,
        @NonNull String audioBitrate,
        @NonNull String libraryVersion
    ) throws SegmentAccessDeniedException {
        val premiumSegmentMappings = libraryManifestRepository.getPremiumSegmentMappings(libraryVersion);
        if (!premiumSegmentMappings.containsKey(soundId)) {
            throw new ConstraintViolationException(String.format("sound with id '%s' doesn't exist", soundId), null);
        }

        // reversed search using startsWith and endsWith to cover bridge segments since they are not
        // explicitly present in the library manifest.
        val isRequestingFreeSegment = premiumSegmentMappings.get(soundId)
            .stream()
            .noneMatch(s -> segmentId.startsWith(s) || segmentId.endsWith(s));

        if (isRequestingFreeSegment && freeAudioBitrates.contains(audioBitrate)) {
            return;
        }

        if (principalId == null) {
            throw new SegmentAccessDeniedException("user is not signed-in");
        }

        if (!subscriptionServiceContract.isUserSubscribed(principalId)) {
            throw new SegmentAccessDeniedException("user doesn't have an active subscription");
        }
    }
}
