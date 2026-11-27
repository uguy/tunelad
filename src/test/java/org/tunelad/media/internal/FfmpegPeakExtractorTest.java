package org.tunelad.media.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.tunelad.media.TestAudio;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real ffmpeg, which must be installed (see README). */
class FfmpegPeakExtractorTest {

    private final FfmpegPeakExtractor extractor =
            new FfmpegPeakExtractor(new FfmpegProperties("ffmpeg", Duration.ofSeconds(30), Duration.ofSeconds(60)));

    @ParameterizedTest
    @ValueSource(strings = {"mp3/Knees_Up.mp3", "audio/tune.m4a", "audio/tune.ogg", "audio/tune.flac", "audio/tune.wav"})
    void every_supported_format_gives_200_peaks_scaled_to_the_loudest(String fixture) {
        var peaks = extractor.peaks(read(fixture)).block();

        assertThat(peaks).hasSize(Peaks.COUNT);
        assertThat(Arrays.stream(peaks).max().orElseThrow()).isEqualTo(1.0);
        assertThat(Arrays.stream(peaks)).allMatch(peak -> peak >= 0 && peak <= 1);
    }

    @Test
    void peaks_follow_the_loudness_of_the_audio() {
        var peaks = extractor.peaks(bytes(TestAudio.silenceThenTone())).block();

        assertThat(Arrays.stream(peaks, 0, 90)).allMatch(peak -> peak < 0.05);
        assertThat(Arrays.stream(peaks, 110, 200)).allMatch(peak -> peak > 0.5);
    }

    @Test
    void random_bytes_cannot_be_decoded() {
        assertThatThrownBy(() -> extractor.peaks(bytes(TestAudio.noise())).block())
                .isInstanceOf(UndecodableAudioException.class);
    }

    @Test
    void missing_ffmpeg_is_told_apart() {
        var withoutFfmpeg = new FfmpegPeakExtractor(
                new FfmpegProperties("/nonexistent/ffmpeg", Duration.ofSeconds(30), Duration.ofSeconds(60)));

        assertThatThrownBy(() -> withoutFfmpeg.peaks(bytes(TestAudio.silenceThenTone())).block())
                .isInstanceOf(FfmpegUnavailableException.class);
    }

    private static Flux<DataBuffer> read(String fixture) {
        return DataBufferUtils.read(new ClassPathResource(fixture), DefaultDataBufferFactory.sharedInstance, 8192);
    }

    private static Flux<DataBuffer> bytes(byte[] audio) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio));
    }
}
