package org.tunelad.media.internal;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PeaksTest {

    @Test
    void peaks_are_scaled_to_the_loudest() {
        var peaks = Peaks.of(new float[]{0.1f, 0.2f, 0.4f, 0.2f}, 4);

        assertThat(peaks).containsExactly(new double[]{0.25, 0.5, 1, 0.5}, within(1e-6));
    }

    @Test
    void each_peak_is_the_largest_of_its_windows() {
        var peaks = Peaks.of(new float[]{0.1f, 0.5f, 0.2f, 0.25f}, 2);

        assertThat(peaks).containsExactly(new double[]{1, 0.5}, within(1e-6));
    }

    @Test
    void silence_gives_zeros() {
        assertThat(Peaks.of(new float[300], Peaks.COUNT)).hasSize(Peaks.COUNT).containsOnly(0);
    }

    @Test
    void no_audio_gives_zeros() {
        assertThat(Peaks.of(new float[0], Peaks.COUNT)).hasSize(Peaks.COUNT).containsOnly(0);
    }

    @Test
    void fewer_windows_than_peaks_still_gives_every_peak() {
        var peaks = Peaks.of(new float[]{0.5f, 1f}, 4);

        assertThat(peaks).containsExactly(new double[]{0.5, 0.5, 1, 1}, within(1e-6));
    }

    @Test
    void accumulator_reads_pcm_split_anywhere() {
        // First half silent, second half at full scale (negative, to check the sign is handled)
        var buffer = ByteBuffer.allocate(Peaks.WINDOW * Peaks.COUNT * 2 * 2).order(ByteOrder.LITTLE_ENDIAN);
        int samples = buffer.capacity() / 2;
        for (int i = 0; i < samples; i++) buffer.putShort(i < samples / 2 ? 0 : Short.MIN_VALUE);
        var pcm = buffer.array();

        var accumulator = new Peaks.Accumulator();
        // Odd-sized pieces split samples across calls
        for (int offset = 0; offset < pcm.length; offset += 777) {
            accumulator.accept(pcm, offset, Math.min(777, pcm.length - offset));
        }

        var peaks = accumulator.peaks();
        assertThat(accumulator.samples()).isEqualTo(samples);
        assertThat(peaks).hasSize(Peaks.COUNT);
        assertThat(java.util.Arrays.copyOfRange(peaks, 0, 100)).containsOnly(0);
        assertThat(java.util.Arrays.copyOfRange(peaks, 100, 200)).containsOnly(1);
    }
}
