package org.tunelad.search.internal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

/**
 * A track as the search read model sees it, kept up to date from track events. Its id is the track's id, so
 * storing the same track again replaces it.
 */
@Document(collection = "search_tracks")
public record SearchTrack(
        @Id String id,
        @TextIndexed String title,
        @TextIndexed String description,
        String artist,
        String album,
        TrackFormat format,
        TrackOwner owner) {

    static SearchTrack of(TrackDTO track) {
        return new SearchTrack(track.id(), track.title(), track.description(), track.artist(), track.album(),
                track.format(), track.owner());
    }
}
