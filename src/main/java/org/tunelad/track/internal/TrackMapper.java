package org.tunelad.track.internal;

import org.springframework.stereotype.Component;
import org.tunelad.set.SetDTO;
import org.tunelad.track.PerformanceDTO;
import org.tunelad.track.TrackDTO;

import java.util.List;

/** Builds the API's view of a Track from the stored one: its Performances are named after the Tunes of its Set. */
@Component
public class TrackMapper {

    public TrackDTO toDto(Track track) {
        var set = track.getTuneSet();
        return new TrackDTO(track.getId(), track.getTitle(), track.getArtist() == null ? null : track.getArtist().name(),
                track.getAlbum(), track.getDescription(), track.getFormat(), track.getOwner(), set, performances(track, set),
                track.isCatalogue(), track.getAlbumId(), track.getAlbumPosition());
    }

    private static List<PerformanceDTO> performances(Track track, SetDTO set) {
        return track.getPerformances().stream().map(performance -> {
            // Unresolved, or no Set to name the Tune from: only the listed name is known
            if (performance.tuneId() == null || set == null) {
                return new PerformanceDTO(performance.position(), performance.tuneId(), null, null,
                        performance.startOffsetSeconds(), performance.key(), performance.notes(), performance.abc(),
                        performance.identification(), performance.listedName());
            }
            var tune = set.tunes().get(performance.position() - 1);
            return new PerformanceDTO(performance.position(), performance.tuneId(), tune.name(), tune.danceType(),
                    performance.startOffsetSeconds(), performance.key(), performance.notes(), performance.abc(),
                    performance.identification(), performance.listedName());
        }).toList();
    }
}
