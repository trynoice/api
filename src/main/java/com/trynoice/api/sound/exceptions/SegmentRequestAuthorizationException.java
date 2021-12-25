package com.trynoice.api.sound.exceptions;

/**
 * Thrown by the authorizeSegmentRequest operation in SoundService.
 */
public class SegmentRequestAuthorizationException extends Exception {

    public SegmentRequestAuthorizationException(String message) {
        super(message);
    }
}
