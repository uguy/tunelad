package org.tunelad.track.internal;

import org.springframework.stereotype.Component;
import org.tunelad.track.AlbumDTO;
import org.tunelad.track.TrackDTO;

import java.util.List;

/** Builds the API's view of an Album from the stored one. */
@Component
class AlbumMapper {

    AlbumDTO toDto(Album album, List<TrackDTO> tracks) {
        var artist = album.getArtist();
        return new AlbumDTO(album.getId(), album.getTitle(), artist == null ? null : artist.name(), album.getArtistId(),
                album.getCoverUrl(), album.getReleaseDate(), tracks);
    }
}
