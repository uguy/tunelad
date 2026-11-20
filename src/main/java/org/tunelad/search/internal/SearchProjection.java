package org.tunelad.search.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;

/**
 * Keeps the search read model up to date from track events.
 * <p>
 * Module listeners run asynchronously on a task executor thread, after the track's transaction committed, and
 * the Event Publication Registry marks the event as handled when the method returns. So each write blocks until it
 * is done: this never runs on an event loop thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SearchProjection {

    private final SearchTracks searchTracks;

    @ApplicationModuleListener
    void on(NewTrackSaved event) {
        // Replaces any previous copy: the registry delivers at least once
        searchTracks.save(SearchTrack.of(event.track())).block();
        log.debug("Track {} is searchable", event.track().id());
    }

    @ApplicationModuleListener
    void on(TrackUpdated event) {
        // Same as a new track: the copy is replaced by id
        searchTracks.save(SearchTrack.of(event.track())).block();
        log.debug("Track {} is searchable with its new metadata", event.track().id());
    }

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        // Idempotent: deleting a copy that is already gone does nothing
        searchTracks.deleteById(event.trackId()).block();
        log.debug("Track {} is no longer searchable", event.trackId());
    }
}
