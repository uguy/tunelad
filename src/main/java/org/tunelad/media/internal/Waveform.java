package org.tunelad.media.internal;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The waveform of the audio stored under a key: {@value Peaks#COUNT} peaks from 0 to 1 once {@link WaveformStatus#READY},
 * none before or when it {@link WaveformStatus#FAILED}.
 */
public record Waveform(String key, WaveformStatus status, List<Double> peaks, @Nullable Instant computedAt) {

    public static Waveform pending(String key) {
        return new Waveform(key, WaveformStatus.PENDING, List.of(), null);
    }
}
