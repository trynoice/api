package com.trynoice.api.sound;

import com.trynoice.api.contracts.SubscriptionServiceContract;
import com.trynoice.api.sound.entities.LibraryManifestRepository;
import com.trynoice.api.sound.exceptions.SegmentAccessDeniedException;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SoundServiceTest {

    @Mock
    private LibraryManifestRepository libraryManifestRepository;

    @Mock
    private SubscriptionServiceContract subscriptionServiceContract;

    @Mock
    private SoundConfiguration soundConfig;

    private SoundService service;

    @BeforeEach
    void setUp() {
        lenient().when(soundConfig.getFreeBitrates()).thenReturn(Set.of("32k", "128k"));
        service = new SoundService(soundConfig, libraryManifestRepository, subscriptionServiceContract);
    }

    @ParameterizedTest
    @MethodSource("authorizeSegmentRequestTestCases")
    void authorizeSegmentRequest(
        Long principalId,
        boolean isPrincipalSubscribed,
        boolean isRequestingPremiumSegment,
        @NonNull String requestedAudioBitrate,
        boolean shouldAllowAccess
    ) {
        val soundId = "test";
        val freeSegmentId = "test_free";
        val premiumSegmentId = "test_premium";
        val premiumSegmentMappings = Map.of(soundId, Set.of(premiumSegmentId));
        val libraryVersion = "test-version";

        when(libraryManifestRepository.getPremiumSegmentMappings(libraryVersion))
            .thenReturn(premiumSegmentMappings);

        if (principalId != null) {
            lenient().when(subscriptionServiceContract.isUserSubscribed(principalId))
                .thenReturn(isPrincipalSubscribed);
        }

        val requestedSegmentId = isRequestingPremiumSegment ? premiumSegmentId : freeSegmentId;
        if (shouldAllowAccess) {
            //noinspection CodeBlock2Expr
            assertDoesNotThrow(() -> {
                service.authorizeSegmentRequest(principalId, soundId, requestedSegmentId, requestedAudioBitrate, libraryVersion);
            });
        } else {
            //noinspection CodeBlock2Expr
            assertThrows(SegmentAccessDeniedException.class, () -> {
                service.authorizeSegmentRequest(principalId, soundId, requestedSegmentId, requestedAudioBitrate, libraryVersion);
            });
        }
    }

    static Stream<Arguments> authorizeSegmentRequestTestCases() {
        return Stream.of(
            // principalId, isPrincipalSubscribed, isRequestingPremiumSegment, requestedAudioBitrate, shouldAllowAccess
            arguments(null, false, false, "32k", true),
            arguments(null, false, false, "320k", false),
            arguments(null, false, true, "32k", false),
            arguments(0L, false, false, "32k", true),
            arguments(0L, false, false, "320k", false),
            arguments(0L, false, true, "32k", false),
            arguments(0L, true, true, "32k", true),
            arguments(0L, true, false, "320k", true)
        );
    }
}
