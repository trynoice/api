package com.trynoice.api.sound;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.sound.exceptions.SegmentAccessDeniedException;
import com.trynoice.api.subscription.SubscriptionService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SoundServiceTest {

    @Mock
    private LibraryManifestRepository libraryManifestRepository;

    @Mock
    private SubscriptionService subscriptionService;

    private SoundService service;

    @BeforeEach
    void setUp() {
        service = new SoundService(libraryManifestRepository, subscriptionService);
    }

    @ParameterizedTest(name = "{displayName} - isPrincipalSubscribed={1}")
    @MethodSource("authorizeSegmentRequestTestCases")
    void authorizeSegmentRequest(AuthUser principal, boolean isPrincipalSubscribed) {
        val soundId = "test";
        val freeSegmentId = "test_free";
        val premiumSegmentId = "test_premium";
        val premiumSegmentMappings = Map.of(soundId, Set.of(premiumSegmentId));

        when(libraryManifestRepository.getPremiumSegmentMappings())
            .thenReturn(premiumSegmentMappings);

        if (principal != null) {
            when(subscriptionService.isUserSubscribed(principal))
                .thenReturn(isPrincipalSubscribed);
        }

        //noinspection CodeBlock2Expr
        assertDoesNotThrow(() -> {
            service.authorizeSegmentRequest(principal, soundId, freeSegmentId);
        });

        if (isPrincipalSubscribed) {
            //noinspection CodeBlock2Expr
            assertDoesNotThrow(() -> {
                service.authorizeSegmentRequest(principal, soundId, premiumSegmentId);
            });
        } else {
            assertThrows(SegmentAccessDeniedException.class, () -> service.authorizeSegmentRequest(principal, soundId, premiumSegmentId));
        }
    }

    static Stream<Arguments> authorizeSegmentRequestTestCases() {
        val principal = mock(AuthUser.class);
        return Stream.of(
            // principal, isPrincipalSubscribed,
            arguments(null, false),
            arguments(principal, false),
            arguments(principal, true)
        );
    }
}
