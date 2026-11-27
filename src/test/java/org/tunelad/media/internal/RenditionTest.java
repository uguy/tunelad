package org.tunelad.media.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.TestAudio;
import org.tunelad.media.event.AudioPrepared;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Renditions within the media module: pending once audio a browser cannot play is stored, then made. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestContainerDefinitions.class, RenditionTest.Recorder.class})
class RenditionTest {

    private static final String WMA = "audio/x-ms-wma";

    /** The keys announced, heard by a module listener as the one that tells clients about them does. */
    static final List<String> announced = new CopyOnWriteArrayList<>();

    @Autowired ChunkedMediaStore store;
    @Autowired Renditions renditions;
    @Autowired RenditionBackfill backfill;
    @Autowired RenditionMaker maker;
    @Autowired DatabaseClient db;
    @Autowired TransactionalEvents events;
    @Autowired @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager;

    @BeforeEach
    void forgetAnnouncements() {
        announced.clear();
    }

    @Test
    void stored_wma_gets_a_ready_aac_rendition_and_is_announced() {
        var key = UUID.randomUUID().toString();

        store.store(key, bytes(TestAudio.wma()), WMA).block();

        awaitStatus(key, RenditionStatus.READY);
        var rendition = store.find(Playable.renditionKey(key)).block();
        assertThat(rendition.contentType()).isEqualTo("audio/mp4");
        // An mp4 file: its first box, after its size, is "ftyp"
        assertThat(new String(read(rendition, 4, 7))).isEqualTo("ftyp");
        await().atMost(Duration.ofSeconds(10)).until(() -> announced.contains(key));
        assertThat(announced).containsOnlyOnce(key);
    }

    @Test
    void the_original_is_kept_unchanged() {
        var key = UUID.randomUUID().toString();
        var wma = TestAudio.wma();

        store.store(key, bytes(wma), WMA).block();

        awaitStatus(key, RenditionStatus.READY);
        var original = store.find(key).block();
        assertThat(original.contentType()).isEqualTo(WMA);
        assertThat(read(original, 0, wma.length - 1)).isEqualTo(wma);
    }

    @Test
    void audio_that_cannot_be_converted_fails_and_is_announced() {
        var key = UUID.randomUUID().toString();

        store.store(key, bytes(TestAudio.fakeWma()), WMA).block();

        awaitStatus(key, RenditionStatus.FAILED);
        assertThat(store.find(Playable.renditionKey(key)).blockOptional()).isEmpty();
        await().atMost(Duration.ofSeconds(10)).until(() -> announced.contains(key));
    }

    @Test
    void without_ffmpeg_the_rendition_stays_pending_and_is_not_announced() {
        var key = UUID.randomUUID().toString();
        store.store(key, bytes(TestAudio.wma()), WMA).block();
        // Back to pending, as if the listener had not run: the other computer is asked in its place
        awaitStatus(key, RenditionStatus.READY);
        renditions.savePending(key).block();
        announced.clear();

        computerWith("/nonexistent/ffmpeg").compute(key).block();

        assertThat(renditions.findStatus(key).block()).isEqualTo(RenditionStatus.PENDING);
        await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3)).until(() -> !announced.contains(key));
    }

    @Test
    void audio_a_browser_plays_gets_no_rendition_and_no_event() {
        var key = UUID.randomUUID().toString();

        store.store(key, bytes(TestAudio.silenceThenTone()), "audio/wav").block();

        // Its waveform is announced by the same event: wait for it to be through
        await().atMost(Duration.ofSeconds(10)).until(() -> count("media_waveforms", key, "status = 'READY'") == 1);
        assertThat(renditions.findStatus(key).blockOptional()).isEmpty();
        assertThat(store.find(Playable.renditionKey(key)).blockOptional()).isEmpty();
        assertThat(announced).doesNotContain(key);
    }

    @Test
    void making_the_rendition_of_deleted_audio_does_nothing() {
        var key = UUID.randomUUID().toString();

        computerWith("ffmpeg").compute(key).block();

        assertThat(store.find(Playable.renditionKey(key)).blockOptional()).isEmpty();
        assertThat(announced).doesNotContain(key);
    }

    @Test
    void audio_deleted_during_the_conversion_leaves_nothing_behind() {
        var key = UUID.randomUUID().toString();
        store.store(key, bytes(TestAudio.wma()), WMA).block();
        awaitStatus(key, RenditionStatus.READY);
        // Pending again, with the audio then deleted under the conversion: the state row goes, the rendition must too
        renditions.savePending(key).block();
        var computer = computerWith("ffmpeg");
        var slow = Flux.defer(() -> store.delete(key).thenMany(Flux.<DataBuffer>empty()));
        maker.using(slow, rendition -> store.storeRendition(key, rendition)).onErrorComplete().block();
        db.sql("DELETE FROM media_renditions WHERE file_key = :key").bind("key", key).then().block();

        computer.compute(key).block();

        assertThat(store.find(Playable.renditionKey(key)).blockOptional()).isEmpty();
    }

    @Test
    void backfill_makes_the_pending_renditions() {
        var key = UUID.randomUUID().toString();
        store.store(key, bytes(TestAudio.wma()), WMA).block();
        awaitStatus(key, RenditionStatus.READY);
        db.sql("DELETE FROM media_files WHERE file_key = :key").bind("key", Playable.renditionKey(key)).then().block();
        renditions.savePending(key).block();
        announced.clear();

        backfill.run().block();

        assertThat(renditions.findStatus(key).block()).isEqualTo(RenditionStatus.READY);
        assertThat(store.find(Playable.renditionKey(key)).blockOptional()).isPresent();
        await().atMost(Duration.ofSeconds(10)).until(() -> announced.contains(key));
    }

    private RenditionComputer computerWith(String ffmpeg) {
        var properties = new FfmpegProperties(ffmpeg, Duration.ofSeconds(30), Duration.ofSeconds(60));
        return new RenditionComputer(store, renditions, new RenditionMaker(properties), events, transactionManager);
    }

    private long count(String table, String key, String where) {
        return db.sql("SELECT count(*) AS n FROM " + table + " WHERE file_key = :key AND " + where)
                .bind("key", key).map(row -> row.get("n", Long.class)).one().block();
    }

    private void awaitStatus(String key, RenditionStatus status) {
        await().atMost(Duration.ofSeconds(20)).until(
                () -> renditions.findStatus(key).blockOptional().orElse(null) == status);
    }

    private static byte[] read(Media media, long first, long last) {
        return DataBufferUtils.join(Flux.from(media.content(first, last)))
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .block();
    }

    private static Flux<DataBuffer> bytes(byte[] audio) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {
        @ApplicationModuleListener
        void on(AudioPrepared event) {
            announced.add(event.key());
        }
    }
}
