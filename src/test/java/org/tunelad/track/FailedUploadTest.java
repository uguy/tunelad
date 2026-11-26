package org.tunelad.track;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.tunelad.AbstractApiTest;
import org.tunelad.media.MediaStore;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for the track spec's "A failed upload leaves nothing behind": a storage failure and an aborted upload leave
 * no track, no stored audio file or chunk, and publish no {@link NewTrackSaved}.
 */
@Import(FailedUploadTest.Config.class)
class FailedUploadTest extends AbstractApiTest {

    /** More than one audio chunk (255 KB), so some chunks are written before the failure. */
    private static final long FAIL_AFTER_BYTES = 600 * 1024;

    static final List<NewTrackSaved> saved = new CopyOnWriteArrayList<>();

    @Autowired FailingMediaStore mediaStore;
    @Autowired TrackService trackService;

    @BeforeEach
    void reset() {
        saved.clear();
        mediaStore.failNextStore = false;
    }

    // --- Requirement: A failed upload leaves nothing behind ---------------------------------------

    @Test
    void storing_the_audio_fails() {
        mediaStore.failNextStore = true;

        upload(Map.of("title", "Broken", "description", "Storage fails half way"), KNEES_UP_MP3)
                .expectStatus().is5xxServerError();

        assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Broken");
        assertThat(count("media_files", null)).isZero();
        assertThat(count("media_chunks", null)).isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    void client_aborts_during_the_upload() throws IOException {
        sendHalfAnUploadThenClose("Half Way");

        // Nothing appears later either: not in the list, not in the audio tables, and not in search within its 5 seconds
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Half Way");
            assertThat(count("media_files", null)).isZero();
            assertThat(count("media_chunks", null)).isZero();
            assertThat(search("half")).isEmpty();
        });
        assertThat(saved).isEmpty();
    }

    /**
     * The server parses the whole multipart body before the controller runs, so an aborted HTTP upload never reaches
     * the audio store. This covers a cancellation while the audio is being written, as when a client goes away
     * during a slow write: the chunks written so far are rolled back.
     */
    @Test
    void upload_cancelled_while_the_audio_is_written() {
        var sent = new AtomicLong();
        // Some audio, then nothing more: the store waits for the rest until the upload is cancelled
        Flux<DataBuffer> audio = Flux.range(0, 4)
                .<DataBuffer>map(i -> {
                    sent.addAndGet(256 * 1024);
                    return DefaultDataBufferFactory.sharedInstance.wrap(new byte[256 * 1024]);
                })
                .concatWith(Sinks.<DataBuffer>empty().asMono());
        var command = new AddTrackCommand("Cancelled", null, null, "Cancelled while stored", audio,
                TrackFormat.MP3, new TrackOwner("account-alice", "Alice Liddell"));

        var upload = trackService.execute(command).subscribe();
        // The chunks are written in a transaction, so they are not visible before it commits: wait for the audio instead
        await().atMost(Duration.ofSeconds(10)).until(() -> sent.get() >= 4 * 256 * 1024);
        upload.dispose();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("media_chunks", null)).isZero();
            assertThat(count("media_files", null)).isZero();
        });
        assertThat(listTracks()).extracting(TrackDTO::title).doesNotContain("Cancelled");
        assertThat(saved).isEmpty();
    }

    /** Sends the start of a multipart upload of the given title, with a large file part, then closes the socket. */
    private void sendHalfAnUploadThenClose(String title) throws IOException {
        var boundary = "tunelad-boundary";
        var head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n" + title + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"description\"\r\n\r\nAborted upload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"half.mp3\"\r\n"
                + "Content-Type: audio/mpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        long announcedLength = head.length + 10L * 1024 * 1024;
        try (var socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/tracks HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Authorization: Bearer " + UPLOADER_TOKEN + "\r\n"
                    + "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n"
                    + "Content-Length: " + announcedLength + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(head);
            out.write(new byte[1024 * 1024]);
            out.flush();
        }
    }

    private List<TrackDTO> search(String q) {
        return client.get().uri(b -> b.path("/api/tracks/search").queryParam("q", "{q}").build(q))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
    }

    /** The real store, except that the next store fails once more than {@link #FAIL_AFTER_BYTES} have been read. */
    static class FailingMediaStore implements MediaStore {

        private final MediaStore delegate;
        volatile boolean failNextStore;

        FailingMediaStore(MediaStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public Mono<Void> store(String key, Publisher<DataBuffer> audio, String contentType) {
            if (!failNextStore) return delegate.store(key, audio, contentType);
            failNextStore = false;
            var read = new AtomicLong();
            Flux<DataBuffer> failing = Flux.from(audio).concatMap(buffer -> {
                if (read.addAndGet(buffer.readableByteCount()) <= FAIL_AFTER_BYTES) return Mono.just(buffer);
                DataBufferUtils.release(buffer);
                return Mono.error(new IOException("Simulated storage failure"));
            });
            return delegate.store(key, failing, contentType);
        }

        @Override
        public Mono<Void> delete(String key) {
            return delegate.delete(key);
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FailingMediaStore failingMediaStore(@Qualifier("chunkedMediaStore") MediaStore chunked) {
            return new FailingMediaStore(chunked);
        }

        @EventListener
        void on(NewTrackSaved event) {
            saved.add(event);
        }
    }
}
