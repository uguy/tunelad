package org.tunelad.media.internal;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.tunelad.media.AudioTags;
import org.tunelad.media.MediaStore;
import org.tunelad.media.TestAudio;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading tags without the application: what a missing or failing ffprobe leaves. */
class FfprobeMediaTagsTest {

    private static MediaStore storing(byte[] audio) {
        return new MediaStore() {
            @Override
            public Mono<Void> store(String key, Publisher<DataBuffer> data, String contentType) {
                return Mono.empty();
            }

            @Override
            public Flux<DataBuffer> read(String key) {
                return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio));
            }

            @Override
            public Mono<Void> delete(String key) {
                return Mono.empty();
            }
        };
    }

    private static FfprobeMediaTags reading(byte[] audio, String ffprobe) {
        return new FfprobeMediaTags(storing(audio), new FfprobeProperties(ffprobe, Duration.ofSeconds(30)));
    }

    @Test
    void reads_the_tags_of_a_file() {
        var tags = reading(TestAudio.tagged("flac", Map.of("title", "Rainfall", "ARTIST", "Breabach")), "ffprobe")
                .read("key").block();

        assertThat(tags.title()).isEqualTo("Rainfall");
        assertThat(tags.artist()).isEqualTo("Breabach");
        assertThat(tags.album()).isNull();
    }

    @Test
    void without_ffprobe_there_are_no_tags() {
        assertThat(reading(TestAudio.tagged("mp3", Map.of("title", "Rainfall")), "/nonexistent/ffprobe")
                .read("key").block()).isEqualTo(AudioTags.NONE);
    }

    @Test
    void audio_that_cannot_be_read_has_no_tags() {
        assertThat(reading(TestAudio.noise(), "ffprobe").read("key").block()).isEqualTo(AudioTags.NONE);
    }
}
