package org.tunelad.search.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.search.internal.SearchTrack;
import org.tunelad.track.TrackFormat;

@Schema(description = "A track matching a search, with the same fields as in the track list")
public record TrackSearchResult(
        String id,
        String title,
        String artist,
        String album,
        String description,
        TrackFormat format) {

    static TrackSearchResult of(SearchTrack track) {
        return new TrackSearchResult(track.id(), track.title(), track.artist(), track.album(), track.description(),
                track.format());
    }
}
