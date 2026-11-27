package org.tunelad.identification;

/**
 * A Tune heard in a recording: its thesession.org id and the span, in seconds from the start of the audio, over which
 * it was played. The start is accurate to a few seconds.
 */
public record DetectedTune(String tuneId, double start, double end, double confidence) {
}
