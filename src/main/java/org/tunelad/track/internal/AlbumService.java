package org.tunelad.track.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.track.AlbumDTO;
import org.tunelad.track.event.AlbumChanged;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/** Albums by hand: created by any authenticated Account, read by anyone. */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final Albums albums;

    private final Tracks tracks;

    private final Artists artists;

    private final TrackMapper trackMapper;

    private final AlbumMapper albumMapper;

    private final TransactionalEvents events;

    /** Fails with 400 for a blank title or Artist name. The Artist is found by name, or created unclassified. */
    @ReactiveTransactional
    public Mono<AlbumDTO> create(String title, String artistName, String coverUrl, LocalDate releaseDate) {
        if (title == null || title.isBlank()) {
            return Mono.error(new BadRequestException("An album needs a title."));
        }
        return artists.findOrCreate(artistName)
                .switchIfEmpty(Mono.error(() -> new BadRequestException("An album needs an artist.")))
                .flatMap(artist -> {
                    var album = new Album();
                    album.setTitle(title.strip());
                    album.setArtist(artist);
                    album.setCoverUrl(blankToNull(coverUrl));
                    album.setReleaseDate(releaseDate);
                    return albums.save(album);
                })
                .map(album -> albumMapper.toDto(album, List.of()))
                .flatMap(dto -> events.publish(new AlbumChanged(dto)).thenReturn(dto));
    }

    /** The Albums by title, without their Tracks. */
    @ReactiveTransactional(readOnly = true)
    public Flux<AlbumDTO> findAll() {
        return albums.findAll().map(album -> albumMapper.toDto(album, List.of()));
    }

    /** The Album with its Tracks in order; fails with 404 when there is none. */
    @ReactiveTransactional(readOnly = true)
    public Mono<AlbumDTO> find(String id) {
        return albums.findById(id)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No album found for id: " + id)))
                .flatMap(album -> tracks.findByAlbumId(id).map(trackMapper::toDto).collectList()
                        .map(list -> albumMapper.toDto(album, list)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
