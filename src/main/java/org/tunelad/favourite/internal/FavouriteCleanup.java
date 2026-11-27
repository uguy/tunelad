package org.tunelad.favourite.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.favourite.event.FavouriteChanged;
import org.tunelad.track.event.TrackDeleted;

/**
 * Takes a deleted track out of every favourites list.
 * <p>
 * Like every module listener it runs on a task executor thread after the deletion committed, so the blocking wait
 * never holds an event loop thread. It is idempotent: the registry delivers at least once, and a second run finds
 * nothing to remove.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FavouriteCleanup {

    private final Favourites favourites;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        var accounts = favourites.deleteByTrack(event.trackId()).collectList().block();
        if (accounts != null) {
            accounts.forEach(account -> events.publishEvent(new FavouriteChanged(account, event.trackId())));
            log.debug("Track {} left the favourites of {} account(s)", event.trackId(), accounts.size());
        }
    }
}
