package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Makes, when the application starts, the rendition of every stored audio that still waits for one: audio stored while
 * ffmpeg was missing, or whose conversion the application stopped in the middle of.
 */
@Slf4j
@Component
class RenditionBackfill {

    private final Renditions renditions;
    private final RenditionComputer computer;

    RenditionBackfill(Renditions renditions, RenditionComputer computer) {
        this.renditions = renditions;
        this.computer = computer;
    }

    /** Runs in the background: startup does not wait for it. */
    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        run().subscribe(
                count -> log.info("Rendition backfill done: {} audio(s) went through", count),
                error -> log.warn("Rendition backfill stopped", error));
    }

    /**
     * Makes the pending renditions one at a time, so a large backlog does not start many ffmpeg processes at once;
     * emits how many it went through.
     */
    Mono<Long> run() {
        return renditions.pendingKeys().collectList()
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .concatMap(key -> computer.compute(key).thenReturn(key))
                .count();
    }
}
