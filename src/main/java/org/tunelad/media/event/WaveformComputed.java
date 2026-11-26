package org.tunelad.media.event;

/**
 * The waveform of the audio stored under the key was saved, as {@code READY} or {@code FAILED}: it never says
 * {@code PENDING}, which a waveform stays in while it cannot be computed. The key is the one the audio is stored under.
 */
public record WaveformComputed(String key) {}
