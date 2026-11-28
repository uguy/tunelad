package org.tunelad.search.internal;

import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

/**
 * A track as the search read model sees it, kept up to date from track events. Its id is the track's id, so
 * storing the same track again replaces it.
 */
public record SearchTrack(
        String id,
        String title,
        String description,
        String artist,
        String album,
        TrackFormat format,
        TrackOwner owner,
        boolean catalogue,
        String albumId,
        Integer albumPosition) {

    /** An ordinary track, with audio and on no album. */
    SearchTrack(String id, String title, String description, String artist, String album, TrackFormat format,
                TrackOwner owner) {
        this(id, title, description, artist, album, format, owner, false, null, null);
    }

    static SearchTrack of(TrackDTO track) {
        return new SearchTrack(track.id(), track.title(), track.description(), track.artist(), track.album(),
                track.format(), track.owner(), Boolean.TRUE.equals(track.catalogue()), track.albumId(), track.albumPosition());
    }
}
