package org.tunelad.media.internal;

/** Where the rendition of audio a browser cannot play stands. Audio that needs no rendition has none of these. */
public enum RenditionStatus {
    /** Not made yet: the audio was just stored, or ffmpeg is not available. */
    PENDING,
    /** Made: the rendition is what is played. */
    READY,
    /** The audio could not be converted; not retried, since the audio does not change. */
    FAILED
}
