package org.tunelad.media.internal;

import java.util.Arrays;

/**
 * Reduces decoded audio to the peaks of a waveform: a fixed number of values from 0 to 1, in playback order, scaled so
 * that the loudest is 1 (all zeros for silence).
 * <p>
 * Samples are first reduced to the maximum amplitude of each small window as they are read, so memory does not grow
 * with the sample rate, only with the duration (a few floats per second); the windows are then grouped into peaks.
 */
public final class Peaks {

    /** The number of peaks of every waveform. */
    public static final int COUNT = 200;

    /** Samples per window: at the 8 kHz the audio is decoded at, 80 windows per second. */
    static final int WINDOW = 100;

    private Peaks() {
    }

    /**
     * Groups window maxima into {@code count} peaks, each the largest of its windows, then scales them to the loudest.
     * With fewer windows than peaks, each window covers several peaks.
     */
    public static double[] of(float[] windowMaxima, int count) {
        var peaks = new double[count];
        if (windowMaxima.length == 0) return peaks;
        for (int i = 0; i < count; i++) {
            int from = (int) ((long) i * windowMaxima.length / count);
            int to = Math.max(from + 1, (int) ((long) (i + 1) * windowMaxima.length / count));
            float max = 0;
            for (int w = from; w < to; w++) max = Math.max(max, windowMaxima[w]);
            peaks[i] = max;
        }
        double loudest = Arrays.stream(peaks).max().orElse(0);
        if (loudest > 0) {
            for (int i = 0; i < count; i++) peaks[i] /= loudest;
        }
        return peaks;
    }

    /** Collects window maxima from signed 16-bit little-endian mono PCM, fed in pieces of any size. */
    public static final class Accumulator {

        private float[] maxima = new float[1024];
        private int windows;
        private int samplesInWindow;
        private float windowMax;
        /** The first byte of a sample split across two pieces, or -1. */
        private int pendingLowByte = -1;
        private long samples;

        public void accept(byte[] pcm, int offset, int length) {
            int i = offset, end = offset + length;
            if (pendingLowByte >= 0 && i < end) {
                sample((short) ((pcm[i++] << 8) | pendingLowByte));
                pendingLowByte = -1;
            }
            for (; i + 1 < end; i += 2) {
                sample((short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff)));
            }
            if (i < end) pendingLowByte = pcm[i] & 0xff;
        }

        private void sample(short value) {
            samples++;
            windowMax = Math.max(windowMax, Math.abs(value / 32768f));
            if (++samplesInWindow == WINDOW) closeWindow();
        }

        private void closeWindow() {
            if (windows == maxima.length) maxima = Arrays.copyOf(maxima, maxima.length * 2);
            maxima[windows++] = windowMax;
            windowMax = 0;
            samplesInWindow = 0;
        }

        /** The number of samples read so far. */
        public long samples() {
            return samples;
        }

        /** The peaks of everything read, including a last partial window. */
        public double[] peaks() {
            if (samplesInWindow > 0) closeWindow();
            return of(Arrays.copyOf(maxima, windows), COUNT);
        }
    }
}
