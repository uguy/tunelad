package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Computes the waveform of stored audio, and saves it as {@link WaveformStatus#READY} or {@link WaveformStatus#FAILED}.
 * <p>
 * Without ffmpeg the waveform stays {@link WaveformStatus#PENDING} (with a single warning), so that the startup
 * backfill computes it once ffmpeg is installed.
 */
@Slf4j
@Component
public class WaveformComputer {

    private final ChunkedMediaStore store;
    private final FfmpegPeakExtractor extractor;
    private final Waveforms waveforms;
    private final AtomicBoolean ffmpegMissingReported = new AtomicBoolean();

    WaveformComputer(ChunkedMediaStore store, FfmpegPeakExtractor extractor, Waveforms waveforms) {
        this.store = store;
        this.extractor = extractor;
        this.waveforms = waveforms;
    }

    /**
     * Runs asynchronously on a task executor thread, after the transaction that stored the audio committed; the Event
     * Publication Registry marks the event as handled when the method returns, so it blocks until the waveform is
     * saved. This never runs on an event loop thread.
     */
    @ApplicationModuleListener
    void on(AudioStored event) {
        compute(event.key()).block();
    }

    /**
     * Computes and saves the waveform of the audio stored under the key. Does nothing when the audio is gone, such as
     * after a failed upload, and only updates an existing waveform, so it never brings back a deleted one.
     */
    public Mono<Void> compute(String key) {
        return store.find(key)
                .flatMap(media -> extractor.peaks(media.content(0, media.length() - 1))
                        .flatMap(peaks -> save(key, WaveformStatus.READY, Arrays.stream(peaks).boxed().toList()))
                        .onErrorResume(UndecodableAudioException.class, error -> {
                            log.info("No waveform for audio {}: {}", key, error.getMessage());
                            return save(key, WaveformStatus.FAILED, List.of());
                        })
                        .onErrorResume(FfmpegUnavailableException.class, error -> {
                            if (ffmpegMissingReported.compareAndSet(false, true)) {
                                log.warn("Waveforms stay pending: {}", error.getMessage());
                            }
                            return Mono.empty();
                        }))
                .doOnSuccess(ignored -> log.debug("Waveform of audio {} computed", key))
                .then();
    }

    private Mono<Void> save(String key, WaveformStatus status, List<Double> peaks) {
        return waveforms.complete(key, status, peaks);
    }
}
