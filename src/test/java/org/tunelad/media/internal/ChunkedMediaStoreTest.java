package org.tunelad.media.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.tunelad.TestContainerDefinitions;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The media module on its own: audio stored under a key, read back whole or by slice. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestContainerDefinitions.class)
class ChunkedMediaStoreTest {

    /** The chunk size of stored audio: slices around it span two chunks. */
    private static final int CHUNK_SIZE = 255 * 1024;

    @Autowired ChunkedMediaStore store;

    @Test
    void stored_audio_reads_back_whole() throws Exception {
        var key = UUID.randomUUID().toString();
        var audio = kneesUp();

        store.store(key, DataBufferUtils.read(new ClassPathResource("mp3/Knees_Up.mp3"),
                DefaultDataBufferFactory.sharedInstance, 8192), "audio/mpeg").block();

        var media = store.find(key).block();
        assertThat(media).isNotNull();
        assertThat(media.length()).isEqualTo(audio.length);
        assertThat(read(media, 0, audio.length - 1)).isEqualTo(audio);
    }

    @Test
    void slice_across_a_chunk_boundary() throws Exception {
        var key = UUID.randomUUID().toString();
        var audio = kneesUp();
        store.store(key, DataBufferUtils.read(new ClassPathResource("mp3/Knees_Up.mp3"),
                DefaultDataBufferFactory.sharedInstance, 8192), "audio/mpeg").block();

        var media = store.find(key).block();

        long first = CHUNK_SIZE - 20, last = CHUNK_SIZE + 20;
        assertThat(read(media, first, last)).isEqualTo(Arrays.copyOfRange(audio, (int) first, (int) last + 1));
    }

    @Test
    void stored_content_type_reads_back() {
        var key = UUID.randomUUID().toString();
        store.store(key, DataBufferUtils.read(new ClassPathResource("mp3/Knees_Up.mp3"),
                DefaultDataBufferFactory.sharedInstance, 8192), "audio/flac").block();

        assertThat(store.find(key).block().contentType()).isEqualTo("audio/flac");
    }

    @Test
    void audio_stored_without_a_content_type_reads_back_as_mpeg() {
        var key = UUID.randomUUID().toString();
        store.store(key, DataBufferUtils.read(new ClassPathResource("mp3/Knees_Up.mp3"),
                DefaultDataBufferFactory.sharedInstance, 8192), "").block();

        assertThat(store.find(key).block().contentType()).isEqualTo("audio/mpeg");
    }

    @Test
    void unknown_key_has_no_audio() {
        assertThat(store.find(UUID.randomUUID().toString()).blockOptional()).isEmpty();
    }

    @Test
    void deleted_audio_is_gone() {
        var key = UUID.randomUUID().toString();
        var other = UUID.randomUUID().toString();
        var resource = new ClassPathResource("mp3/Knees_Up.mp3");
        store.store(key, DataBufferUtils.read(resource, DefaultDataBufferFactory.sharedInstance, 8192), "audio/mpeg").block();
        store.store(other, DataBufferUtils.read(resource, DefaultDataBufferFactory.sharedInstance, 8192), "audio/mpeg").block();

        store.delete(key).block();

        assertThat(store.find(key).blockOptional()).isEmpty();
        assertThat(store.find(other).blockOptional()).isPresent();
    }

    @Test
    void deleting_a_missing_key_completes() {
        store.delete(UUID.randomUUID().toString()).block();
    }

    private static byte[] kneesUp() throws Exception {
        return new ClassPathResource("mp3/Knees_Up.mp3").getContentAsByteArray();
    }

    private static byte[] read(Media media, long first, long last) {
        return DataBufferUtils.join(Flux.from(media.content(first, last)))
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                })
                .block();
    }
}
