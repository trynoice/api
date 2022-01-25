package com.trynoice.api.sound;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.sound.exceptions.SegmentAccessDeniedException;
import com.trynoice.api.subscription.SubscriptionService;
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
    private final SubscriptionService subscriptionService;

    @Autowired
    SoundService(
        @NonNull SoundConfiguration soundConfig,
        @NonNull LibraryManifestRepository libraryManifestRepository,
        @NonNull SubscriptionService subscriptionService
    ) {
        this.libraryManifestRepository = libraryManifestRepository;
        this.freeAudioBitrates = soundConfig.getFreeBitrates();
        this.subscriptionService = subscriptionService;
    }

    /**
     * Authorizes a request for the given {@code segmentId} of the given {@code soundId}. If the
     * requested segment is premium, {@code principal} must be non-null, and must have an active
     * subscription. Throws {@link SegmentAccessDeniedException} if {@code principal} is not
     * authorized to access the requested segment.
     *
     * @param principal an {@code nullable} {@link AuthUser} that made this request.
     * @param soundId   the id of the sound being requested.
     * @param segmentId the id of the segment being requested.
     * @throws SegmentAccessDeniedException if {@code principal} requests a premium segment but
     *                                      isn't signed-in ({@code null}) or doesn't have an active
     *                                      subscription.
     */
    void authorizeSegmentRequest(
        AuthUser principal,
        @NonNull String soundId,
        @NonNull String segmentId,
        String audioBitrate
    ) throws SegmentAccessDeniedException {
        val premiumSegmentMappings = libraryManifestRepository.getPremiumSegmentMappings();
        if (!premiumSegmentMappings.containsKey(soundId)) {
            throw new ConstraintViolationException(String.format("sound with id '%s' doesn't exist", soundId), null);
        }

        // reversed search using startsWith and endsWith to cover bridge segments since they are not
        // explicitly present in the library manifest.
        val isRequestingFreeSegment = premiumSegmentMappings.get(soundId)
            .stream()
            .noneMatch(s -> segmentId.startsWith(s) || segmentId.endsWith(s));

        // audio bitrate will not be available when requesting master playlists.
        val isRequestFreeBitrate = audioBitrate == null || audioBitrate.isBlank() || freeAudioBitrates.contains(audioBitrate);
        if (isRequestingFreeSegment && isRequestFreeBitrate) {
            return;
        }

        if (principal == null) {
            throw new SegmentAccessDeniedException("user is not signed-in");
        }

        if (!subscriptionService.isUserSubscribed(principal)) {
            throw new SegmentAccessDeniedException("user doesn't have an active subscription");
        }
    }
}
