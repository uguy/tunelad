package org.tunelad.media.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.media.TestAudio;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Waveforms within the media module: pending once audio is stored, then computed by the listener or the backfill. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestContainerDefinitions.class)
class WaveformTest {

    @Autowired ChunkedMediaStore store;
    @Autowired Waveforms waveforms;
    @Autowired DatabaseClient db;
    @Autowired WaveformBackfill backfill;

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
        var withoutFfmpeg = new WaveformComputer(store,
                new FfmpegPeakExtractor(new FfmpegProperties("/nonexistent/ffmpeg", Duration.ofSeconds(30))), waveforms);

        withoutFfmpeg.compute(key).block();

        assertThat(waveforms.findById(key).block().status()).isEqualTo(WaveformStatus.PENDING);
    }

    @Test
    void computing_the_waveform_of_deleted_audio_does_nothing() {
        var key = UUID.randomUUID().toString();
        var computer = new WaveformComputer(store,
                new FfmpegPeakExtractor(new FfmpegProperties("ffmpeg", Duration.ofSeconds(30))), waveforms);

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
