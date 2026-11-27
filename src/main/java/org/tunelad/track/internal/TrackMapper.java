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
                track.getAlbum(), track.getDescription(), track.getFormat(), track.getOwner(), set, performances(track, set));
    }

    private static List<PerformanceDTO> performances(Track track, SetDTO set) {
        if (set == null) {
            return List.of();
        }
        return track.getPerformances().stream().map(performance -> {
            var tune = set.tunes().get(performance.position() - 1);
            return new PerformanceDTO(performance.position(), performance.tuneId(), tune.name(), tune.danceType(),
                    performance.startOffsetSeconds(), performance.key(), performance.notes());
        }).toList();
    }
}
