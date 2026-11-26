package org.tunelad.media.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.transaction.ReactiveTransactionManager;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.event.WaveformComputed;
import org.tunelad.media.TestAudio;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Waveforms within the media module: pending once audio is stored, then computed by the listener or the backfill. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestContainerDefinitions.class, WaveformTest.Recorder.class})
class WaveformTest {

    /** The keys of the waveforms announced, heard by a module listener as the ones that tell clients about them do. */
    static final List<String> announced = new CopyOnWriteArrayList<>();

    @Autowired ChunkedMediaStore store;
    @Autowired Waveforms waveforms;
    @Autowired DatabaseClient db;
    @Autowired WaveformBackfill backfill;
    @Autowired TransactionalEvents events;
    @Autowired @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager;

    @BeforeEach
    void forgetAnnouncements() {
        announced.clear();
    }

    @Test
    void storing_audio_announces_its_waveform(Scenario scenario) {
        var key = UUID.randomUUID().toString();

        scenario.stimulate(() -> store.store(key, fixture("audio/tune.wav"), "audio/wav").block())
                .andWaitForEventOfType(AudioStored.class)
                .matching(event -> event.key().equals(key))
                .toArrive();
    }

    @Test
    void stored_audio_gets_a_ready_waveform_that_follows_its_loudness() {
        var key = UUID.randomUUID().toString();

        store.store(key, bytes(TestAudio.silenceThenTone()), "audio/wav").block();

        var waveform = awaitStatus(key, WaveformStatus.READY);
        assertThat(waveform.peaks()).hasSize(Peaks.COUNT);
        assertThat(waveform.peaks().subList(0, 90)).allMatch(peak -> peak < 0.05);
        assertThat(waveform.peaks().subList(110, 200)).allMatch(peak -> peak > 0.5);
        assertThat(waveform.computedAt()).isNotNull();
    }

    @Test
    void undecodable_audio_gets_a_failed_waveform() {
        var key = UUID.randomUUID().toString();

        store.store(key, bytes(TestAudio.noise()), "audio/mpeg").block();

        assertThat(awaitStatus(key, WaveformStatus.FAILED).peaks()).isEmpty();
    }

    @Test
    void deleting_audio_deletes_its_waveform() {
        var key = UUID.randomUUID().toString();
        store.store(key, fixture("audio/tune.wav"), "audio/wav").block();
        awaitStatus(key, WaveformStatus.READY);

        store.delete(key).block();

        assertThat(waveforms.findById(key).blockOptional()).isEmpty();
    }

    @Test
    void without_ffmpeg_the_waveform_stays_pending() {
        var key = UUID.randomUUID().toString();
        storeWithoutAnnouncing(key, "audio/tune.wav");
        waveforms.savePending(key).block();
        var withoutFfmpeg = computerWith(new FfmpegProperties("/nonexistent/ffmpeg", Duration.ofSeconds(30)));

        withoutFfmpeg.compute(key).block();

        assertThat(waveforms.findById(key).block().status()).isEqualTo(WaveformStatus.PENDING);
    }

    @Test
    void computing_the_waveform_of_deleted_audio_does_nothing() {
        var key = UUID.randomUUID().toString();
        var computer = computerWith(new FfmpegProperties("ffmpeg", Duration.ofSeconds(30)));

        computer.compute(key).block();

        assertThat(waveforms.findById(key).blockOptional()).isEmpty();
    }

    @Test
    void backfill_computes_the_waveform_of_audio_stored_without_one() {
        var key = UUID.randomUUID().toString();
        // As audio stored before waveforms existed: straight into the tables, with no waveform
        storeWithoutAnnouncing(key, "mp3/Knees_Up.mp3");

        backfill.run().block();

        assertThat(waveforms.findById(key).block().status()).isEqualTo(WaveformStatus.READY);
    }

    // --- WaveformComputed ---------------------------------------------------------------------

    @Test
    void a_ready_waveform_is_announced_once_it_is_saved(Scenario scenario) {
        var key = UUID.randomUUID().toString();

        scenario.stimulate(() -> store.store(key, bytes(TestAudio.silenceThenTone()), "audio/wav").block())
                .andWaitForEventOfType(WaveformComputed.class)
                .matching(event -> event.key().equals(key))
                .toArriveAndVerify(event ->
                        assertThat(waveforms.findById(key).block().status()).isEqualTo(WaveformStatus.READY));
    }

    @Test
    void a_failed_waveform_is_announced_once_it_is_saved(Scenario scenario) {
        var key = UUID.randomUUID().toString();

        scenario.stimulate(() -> store.store(key, bytes(TestAudio.noise()), "audio/mpeg").block())
                .andWaitForEventOfType(WaveformComputed.class)
                .matching(event -> event.key().equals(key))
                .toArriveAndVerify(event ->
                        assertThat(waveforms.findById(key).block().status()).isEqualTo(WaveformStatus.FAILED));
    }

    @Test
    void a_backfilled_waveform_is_announced_to_module_listeners() {
        var key = UUID.randomUUID().toString();
        storeWithoutAnnouncing(key, "mp3/Knees_Up.mp3");

        // The backfill runs outside any transaction of its own, where a plain publish would reach no module listener
        backfill.run().block();

        await().atMost(Duration.ofSeconds(10)).until(() -> announced.contains(key));
        assertThat(announced).containsOnlyOnce(key);
    }

    @Test
    void a_waveform_that_stays_pending_is_not_announced() {
        var key = UUID.randomUUID().toString();
        storeWithoutAnnouncing(key, "audio/tune.wav");
        waveforms.savePending(key).block();

        computerWith(new FfmpegProperties("/nonexistent/ffmpeg", Duration.ofSeconds(30))).compute(key).block();

        await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3)).until(() -> !announced.contains(key));
    }

    @Test
    void a_waveform_that_is_gone_is_not_announced() {
        var key = UUID.randomUUID().toString();
        // Audio without a waveform row, as after its deletion while the computation was under way
        storeWithoutAnnouncing(key, "mp3/Knees_Up.mp3");

        computerWith(new FfmpegProperties("ffmpeg", Duration.ofSeconds(30))).compute(key).block();

        assertThat(waveforms.findById(key).blockOptional()).isEmpty();
        await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3)).until(() -> !announced.contains(key));
    }

    private WaveformComputer computerWith(FfmpegProperties ffmpeg) {
        return new WaveformComputer(store, new FfmpegPeakExtractor(ffmpeg), waveforms, events, transactionManager);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {
        @ApplicationModuleListener
        void on(WaveformComputed event) {
            announced.add(event.key());
        }
    }

    /** Puts audio in the tables as one chunk, without the event and the pending waveform that storing it brings. */
    private void storeWithoutAnnouncing(String key, String path) {
        var audio = DataBufferUtils.join(fixture(path)).map(buffer -> {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return bytes;
        }).block();
        db.sql("INSERT INTO media_files (file_key, length, chunk_size, content_type) VALUES (:key, :length, :size, 'audio/mpeg')")
                .bind("key", key).bind("length", (long) audio.length).bind("size", audio.length).then().block();
        db.sql("INSERT INTO media_chunks (file_key, n, data) VALUES (:key, 0, :data)")
                .bind("key", key).bind("data", java.nio.ByteBuffer.wrap(audio)).then().block();
    }

    private Waveform awaitStatus(String key, WaveformStatus status) {
        await().atMost(Duration.ofSeconds(10)).until(
                () -> waveforms.findById(key).blockOptional().map(Waveform::status).orElse(null) == status);
        return waveforms.findById(key).block();
    }

    private static Flux<DataBuffer> fixture(String path) {
        return DataBufferUtils.read(new ClassPathResource(path), DefaultDataBufferFactory.sharedInstance, 8192);
    }

    private static Flux<DataBuffer> bytes(byte[] audio) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio));
    }
}
