package org.tunelad.track;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrackFormatTest {

    @ParameterizedTest
    @CsvSource({
            "tune.mp3, MP3, audio/mpeg",
            "tune.m4a, M4A, audio/mp4",
            "tune.ogg, OGG, audio/ogg",
            "tune.flac, FLAC, audio/flac",
            "tune.wav, WAV, audio/wav",
            "tune.wma, WMA, audio/x-ms-wma",
            "TUNE.WMA, WMA, audio/x-ms-wma",
            "TUNE.FLAC, FLAC, audio/flac",
            "my.live.tune.Mp3, MP3, audio/mpeg"
    })
    void supported_extension(String filename, TrackFormat format, String mimeType) {
        assertThat(TrackFormat.forFile(filename)).contains(format);
        assertThat(format.mimeType()).isEqualTo(mimeType);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"notes.txt", "tune", "tune.", "mp3", "tune.mp3.zip"})
    void unsupported_file(String filename) {
        assertThat(TrackFormat.forFile(filename)).isEmpty();
    }
}
