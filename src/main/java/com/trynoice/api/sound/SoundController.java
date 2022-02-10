package com.trynoice.api.sound;


import com.trynoice.api.sound.exceptions.SegmentAccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * REST controller for '{@code /v1/sounds}' routes.
 */
@Validated
@RestController
@RequestMapping("/v1/sounds")
@Slf4j
@Tag(name = "sound")
class SoundController {

    private final SoundService soundService;

    @Autowired
    SoundController(@NonNull SoundService soundService) {
        this.soundService = soundService;
    }

    /**
     * Authorizes a request for a sound segment based on the signed-in user's subscription status.
     * If a premium segment or bitrate is being requested, the user must be signed-in and have an
     * active subscription. Otherwise, HTTP 401 is returned.
     *
     * @param soundId      id of the sound to which the requested segment belongs.
     * @param segmentId    id of the requested segment.
     * @param audioBitrate bitrate of the requested hls playlist or segment, e.g. 32k or 128k.
     * @return <ul>
     * <li>{@code HTTP 204} if request is authorized.</li>
     * <li>{@code HTTP 400} if request is not valid.</li>
     * <li>{@code HTTP 401} if user requested a premium segment but is not signed-in.</li>
     * <li>{@code HTTP 403} if user requested a premium segment but doesn't have an active subscription.</li>
     * <li>{@code HTTP 500} on internal server errors.</li>
     * </ul>
     */

    @Operation(hidden = true)
    @NonNull
    @GetMapping("/{soundId}/segments/{segmentId}/authorize")
    ResponseEntity<Void> authorizeSegmentRequest(
        @AuthenticationPrincipal Long principalId,
        @Valid @NotBlank @PathVariable String soundId,
        @Valid @NotBlank @PathVariable String segmentId,
        @RequestParam(required = false) String audioBitrate
    ) {
        try {
            soundService.authorizeSegmentRequest(principalId, soundId, segmentId, audioBitrate);
            return ResponseEntity.noContent().build();
        } catch (SegmentAccessDeniedException e) {
            log.trace("segment request denied", e);
            return ResponseEntity.status(principalId == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN).build();
        }
    }
}
