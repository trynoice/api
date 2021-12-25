package com.trynoice.api.sound;


import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.sound.exceptions.SegmentRequestAuthorizationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
class SoundController {

    private final SoundService soundService;

    @Autowired
    SoundController(@NonNull SoundService soundService) {
        this.soundService = soundService;
    }

    /**
     * Authorizes a request for a sound segment based on the signed-in user's subscription status.
     * If a premium segment is being requested, the user must be signed-in and have an active
     * subscription. Otherwise, HTTP 401 is returned.
     *
     * @param soundId   id of the sound to which the requested segment belongs
     * @param segmentId id of the requested segment.
     */
    @Operation(summary = "Authorize access to a segment of a sound")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "request is authorized"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "401", description = "request is not authorized"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @GetMapping("/{soundId}/segments/{segmentId}/authorize")
    ResponseEntity<Void> authorizeSegmentRequest(
        @AuthenticationPrincipal AuthUser principal,
        @Valid @NotBlank @PathVariable String soundId,
        @Valid @NotBlank @PathVariable String segmentId
    ) {
        try {
            soundService.authorizeSegmentRequest(principal, soundId, segmentId);
            return ResponseEntity.ok(null);
        } catch (SegmentRequestAuthorizationException e) {
            log.trace("segment request is not authorized", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
