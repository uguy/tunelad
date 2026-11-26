package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashSet;

/**
 * Computes, when the application starts, the waveform of every stored audio that has none: audio stored before
 * waveforms existed, or while ffmpeg was missing.
 */
@Slf4j
@Component
class WaveformBackfill {

    private final ChunkedMediaStore store;
    private final Waveforms waveforms;
    private final WaveformComputer computer;

    WaveformBackfill(ChunkedMediaStore store, Waveforms waveforms, WaveformComputer computer) {
        this.store = store;
        this.waveforms = waveforms;
        this.computer = computer;
    }

    /** Runs in the background: startup does not wait for it. */
    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        run().subscribe(
                count -> log.info("Waveform backfill done: {} waveform(s) computed", count),
                error -> log.warn("Waveform backfill stopped", error));
    }

    /**
     * Computes the missing waveforms one at a time, so a large backlog does not start many ffmpeg processes at once;
     * emits how many it went through.
     */
    Mono<Long> run() {
        return waveforms.computedKeys().collect(HashSet<String>::new, HashSet::add)
                .flatMapMany(computed -> store.keys().filter(key -> !computed.contains(key)))
                .concatMap(key -> waveforms.ensurePending(key)
                        .then(computer.compute(key))
                        .thenReturn(key))
                .count();
    }
}
