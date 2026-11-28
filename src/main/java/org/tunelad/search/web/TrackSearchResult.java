package org.tunelad.search.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.search.internal.SearchTrack;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

@Schema(description = "A track matching a search, with the same fields as in the track list")
public record TrackSearchResult(
        String id,
        String title,
        String artist,
        String album,
        String description,
        TrackFormat format,
        TrackOwner owner,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True for a catalogue track: a place on an album with no audio yet")
        boolean catalogue,
        @Schema(description = "The album this track is on; absent when it is on none", nullable = true)
        String albumId,
        @Schema(description = "The track's place on its album, from 1; absent when it is on no album", nullable = true)
        Integer albumPosition) {

    static TrackSearchResult of(SearchTrack track) {
        return new TrackSearchResult(track.id(), track.title(), track.artist(), track.album(), track.description(),
                track.format(), track.owner(), track.catalogue(), track.albumId(), track.albumPosition());
    }
}
