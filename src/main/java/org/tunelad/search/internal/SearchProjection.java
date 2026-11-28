package org.tunelad.search.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.tunelad.search.event.TrackIndexed;
import org.tunelad.search.event.TrackUnindexed;
import org.tunelad.track.event.AlbumChanged;
import org.tunelad.track.event.CatalogueTrackAdded;
import org.tunelad.track.event.NewTrackSaved;
import org.tunelad.track.event.TrackDeleted;
import org.tunelad.track.event.TrackUpdated;

/**
 * Keeps the search read model up to date from track events.
 * <p>
 * Module listeners run asynchronously on a task executor thread, after the track's transaction committed, and
 * the Event Publication Registry marks the event as handled when the method returns. So each write blocks until it
 * is done: this never runs on an event loop thread.
 * <p>
 * Each change is announced once it is written ({@link TrackIndexed}, {@link TrackUnindexed}), within the listener's
 * own transaction, so that whoever tells clients about it never does so before a search can see the change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SearchProjection {

    private final SearchTracks searchTracks;
    private final SearchAlbums searchAlbums;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void on(NewTrackSaved event) {
        // Replaces any previous copy: the registry delivers at least once
        searchTracks.save(SearchTrack.of(event.track())).block();
        events.publishEvent(new TrackIndexed(event.track().id()));
        log.debug("Track {} is searchable", event.track().id());
    }

    @ApplicationModuleListener
    void on(CatalogueTrackAdded event) {
        searchTracks.save(SearchTrack.of(event.track())).block();
        events.publishEvent(new TrackIndexed(event.track().id()));
        log.debug("Catalogue track {} is searchable", event.track().id());
    }

    /** Replaces the Album's copy; the registry delivers at least once. */
    @ApplicationModuleListener
    void on(AlbumChanged event) {
        searchAlbums.save(event.album()).block();
        log.debug("Album {} is searchable", event.albumId());
    }

    @ApplicationModuleListener
    void on(TrackUpdated event) {
        // Same as a new track: the copy is replaced by id
        searchTracks.save(SearchTrack.of(event.track())).block();
        events.publishEvent(new TrackIndexed(event.track().id()));
        log.debug("Track {} is searchable with its new metadata", event.track().id());
    }

    @ApplicationModuleListener
    void on(TrackDeleted event) {
        // Idempotent: deleting a copy that is already gone does nothing
        searchTracks.deleteById(event.trackId()).block();
        events.publishEvent(new TrackUnindexed(event.trackId()));
        log.debug("Track {} is no longer searchable", event.trackId());
    }
}
