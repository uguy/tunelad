package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.event.AudioPrepared;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes the rendition of stored audio a browser cannot play, and saves it as {@link RenditionStatus#READY} or
 * {@link RenditionStatus#FAILED}; the audio is then played from it.
 * <p>
 * Without ffmpeg the rendition stays {@link RenditionStatus#PENDING} (with a single warning), so that the startup
 * backfill makes it once ffmpeg is installed.
 */
@Slf4j
@Component
public class RenditionComputer {

    private final ChunkedMediaStore store;
    private final Renditions renditions;
    private final RenditionMaker maker;
    private final TransactionalEvents events;
    private final TransactionalOperator transactionalOperator;
    private final AtomicBoolean ffmpegMissingReported = new AtomicBoolean();

    RenditionComputer(ChunkedMediaStore store, Renditions renditions, RenditionMaker maker, TransactionalEvents events,
                      @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
        this.store = store;
        this.renditions = renditions;
        this.maker = maker;
        this.events = events;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Runs asynchronously on a task executor thread, after the transaction that stored the audio committed; the Event
     * Publication Registry marks the event as handled when the method returns, so it blocks until the rendition is
     * saved. This never runs on an event loop thread.
     */
    @ApplicationModuleListener
    void on(AudioStored event) {
        compute(event.key()).block();
    }

    /**
     * Makes and saves the rendition of the audio stored under the key. Does nothing when the audio needs none, is
     * gone (such as after a failed upload) or has its rendition already, and never brings back a deleted audio.
     */
    public Mono<Void> compute(String key) {
        return renditions.findStatus(key)
                .filter(status -> status == RenditionStatus.PENDING)
                .flatMap(pending -> store.find(key))
                .flatMap(media -> maker.using(media.content(0, media.length() - 1), rendition -> store.storeRendition(key, rendition))
                        .then(Mono.defer(() -> save(key, RenditionStatus.READY)))
                        .onErrorResume(UndecodableAudioException.class, error -> {
                            log.info("No rendition for audio {}: {}", key, error.getMessage());
                            return save(key, RenditionStatus.FAILED);
                        })
                        .onErrorResume(FfmpegUnavailableException.class, error -> {
                            if (ffmpegMissingReported.compareAndSet(false, true)) {
                                log.warn("Renditions stay pending: {}", error.getMessage());
                            }
                            return Mono.empty();
                        }))
                .doOnSuccess(ignored -> log.debug("Rendition of audio {} made", key))
                .then();
    }

    /**
     * Saves the outcome and announces it, in a transaction of its own: the startup backfill runs outside any
     * transaction, and module listeners only hear about events published within one. Nothing is announced when no
     * rendition was updated, which means the audio was deleted meanwhile: what was stored for it goes too.
     */
    private Mono<Void> save(String key, RenditionStatus status) {
        return renditions.complete(key, status)
                .flatMap(updated -> updated > 0
                        ? events.publish(new AudioPrepared(key))
                        : store.delete(key))
                .as(transactionalOperator::transactional);
    }
}
