package org.tunelad.media.internal;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Computes, when the application starts, the waveform of every stored audio that has none: audio stored before
 * waveforms existed, or while ffmpeg was missing.
 */
@Slf4j
@Component
class WaveformBackfill {

    private final ReactiveGridFsOperations gridFs;
    private final ReactiveMongoOperations mongo;
    private final Waveforms waveforms;
    private final WaveformComputer computer;

    WaveformBackfill(ReactiveGridFsOperations gridFs, ReactiveMongoOperations mongo, Waveforms waveforms,
                     WaveformComputer computer) {
        this.gridFs = gridFs;
        this.mongo = mongo;
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
        var done = query(where("status").in(List.of(WaveformStatus.READY, WaveformStatus.FAILED)));
        return mongo.find(done, Waveform.class).map(Waveform::key).collect(HashSet<String>::new, HashSet::add)
                .flatMapMany(computed -> gridFs.find(new Query())
                        .map(GridFSFile::getFilename)
                        .filter(key -> !computed.contains(key)))
                .concatMap(key -> waveforms.findById(key)
                        .switchIfEmpty(Mono.defer(() -> waveforms.save(Waveform.pending(key))))
                        .then(computer.compute(key))
                        .thenReturn(key))
                .count();
    }
}
