package org.tunelad.media.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.tunelad.media.TestAudio;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real ffmpeg and ffprobe, which must be installed (see README). */
class RenditionMakerTest {

    private final RenditionMaker maker = new RenditionMaker(
            new FfmpegProperties("ffmpeg", Duration.ofSeconds(30), Duration.ofSeconds(60)));

    @Test
    void the_conversion_timeout_defaults_to_five_minutes_and_can_be_set() {
        var defaults = new Binder(new MapConfigurationPropertySource(Map.of("tunelad.media.ffmpeg.path", "ffmpeg")))
                .bind("tunelad.media.ffmpeg", FfmpegProperties.class).get();
        var set = new Binder(new MapConfigurationPropertySource(Map.of("tunelad.media.ffmpeg.transcode-timeout", "90s")))
                .bind("tunelad.media.ffmpeg", FfmpegProperties.class).get();

        assertThat(defaults.transcodeTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(set.transcodeTimeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void a_wma_file_becomes_aac_of_about_the_same_length() throws Exception {
        var rendition = maker.using(bytes(TestAudio.wma()), TestAudioConversion::join).block();

        var probe = probe(rendition);
        assertThat(probe).contains("codec_name=aac");
        assertThat(Double.parseDouble(probe.lines().filter(l -> l.startsWith("duration=")).findFirst().orElseThrow()
                .substring("duration=".length()))).isBetween(3.5, 4.5);
    }

    @Test
    void the_temporary_file_is_deleted_once_the_rendition_is_used() throws Exception {
        var before = renditionFiles();

        maker.using(bytes(TestAudio.wma()), TestAudioConversion::join).block();

        assertThat(renditionFiles()).isSubsetOf(before);
    }

    @Test
    void audio_that_cannot_be_converted_fails_and_leaves_no_file() throws Exception {
        var before = renditionFiles();

        assertThatThrownBy(() -> maker.using(bytes(TestAudio.fakeWma()), TestAudioConversion::join).block())
                .isInstanceOf(UndecodableAudioException.class);

        assertThat(renditionFiles()).isSubsetOf(before);
    }

    @Test
    void the_temporary_file_is_deleted_when_using_the_rendition_fails() throws Exception {
        var before = renditionFiles();

        assertThatThrownBy(() -> maker.<byte[]>using(bytes(TestAudio.wma()),
                rendition -> Mono.error(new IllegalStateException("store failed"))).block())
                .isInstanceOf(IllegalStateException.class);

        assertThat(renditionFiles()).isSubsetOf(before);
    }

    @Test
    void missing_ffmpeg_is_told_apart() {
        var without = new RenditionMaker(new FfmpegProperties("/nonexistent/ffmpeg", Duration.ofSeconds(30), Duration.ofSeconds(60)));

        assertThatThrownBy(() -> without.using(bytes(TestAudio.wma()), TestAudioConversion::join).block())
                .isInstanceOf(FfmpegUnavailableException.class);
    }

    /** What the rendition's reader does with it: all its bytes. */
    private static final class TestAudioConversion {
        static Mono<byte[]> join(Flux<DataBuffer> rendition) {
            return DataBufferUtils.join(rendition).map(buffer -> {
                var bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                return bytes;
            });
        }
    }

    private static Flux<DataBuffer> bytes(byte[] audio) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio));
    }

    private static String probe(byte[] audio) throws IOException, InterruptedException {
        var file = Files.createTempFile("probe-", ".m4a");
        try {
            Files.write(file, audio);
            var process = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                    "stream=codec_name:format=duration", "-of", "default=nw=1", file.toString())
                    .redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            return output;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Set<String> renditionFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("tunelad-rendition-")).collect(Collectors.toSet());
        }
    }
}
