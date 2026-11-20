package org.tunelad.media.internal;

/** Where the computation of a waveform stands. */
public enum WaveformStatus {
    /** Not computed yet: the audio was just stored, or ffmpeg is not available. */
    PENDING,
    /** Computed: the waveform has its peaks. */
    READY,
    /** The audio could not be decoded; not retried, since the audio does not change. */
    FAILED
}
