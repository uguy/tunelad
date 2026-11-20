package org.tunelad.media.internal;

/** Audio was stored under the key, and its waveform waits to be computed. Internal to the media module. */
public record AudioStored(String key) {
}
