package org.tunelad.playlist.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.playlist.event.PlaylistChanged;
import org.tunelad.track.event.TrackDeleted;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Takes a deleted track out of every playlist that held it, keeping the order of the others.
 * <p>
 * Like every module listener it runs on a task executor thread after the deletion committed, so the blocking wait
 * never holds an event loop thread. It is idempotent: the registry delivers at least once, and a second run finds
 * nothing to remove. The removal and the renumbering of each playlist are one transaction.
 */
@Slf4j
@Component
class PlaylistCleanup {

    private final Playlists playlists;
    private final ApplicationEventPublisher events;
    private final TransactionalOperator transaction;

    PlaylistCleanup(Playlists playlists, ApplicationEventPublisher events,
                    @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
        this.playlists = playlists;
        this.events = events;
        this.transaction = TransactionalOperator.create(transactionManager);
    }

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        List<PlaylistChanged> changes = playlists.deleteItemsByTrack(event.trackId()).collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(playlistId -> playlists.renumber(playlistId)
                        .then(playlists.touch(playlistId))
                        .then(playlists.ownerOf(playlistId))
                        .map(owner -> new PlaylistChanged(owner, playlistId)))
                .collectList()
                .as(transaction::transactional)
                .block();
        if (changes != null) {
            changes.forEach(events::publishEvent);
            log.debug("Track {} left {} playlist(s)", event.trackId(), changes.size());
        }
    }
}
