package com.trynoice.api.sound.exceptions;

/**
 * Thrown by the authorizeSegmentRequest operation in SoundService.
 */
public class SegmentAccessDeniedException extends Exception {

    public SegmentAccessDeniedException(String message) {
        super(message);
    }
}
