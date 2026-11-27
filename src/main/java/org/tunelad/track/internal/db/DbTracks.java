package org.tunelad.track.internal.db;

import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Artist;
import org.tunelad.track.internal.Artists;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Component
class DbTracks implements Tracks {

    private final R2dbcTrackRepository trackRepository;

    private final Artists artists;

    DbTracks(R2dbcTrackRepository trackRepository, Artists artists) {
        this.trackRepository = trackRepository;
        this.artists = artists;
    }

    @Override
    public Flux<Track> findAll() {
        return withArtists(trackRepository.findAll());
    }

    @Override
    public Flux<Track> findAllById(java.util.Collection<String> ids) {
        return ids.isEmpty() ? Flux.empty() : withArtists(trackRepository.findAllById(ids));
    }

    @Override
    public Mono<Track> save(Track track) {
        if (track.getId() == null || track.getId().isBlank()) {
            track.setId(UUID.randomUUID().toString());
        }
        var artist = track.getArtist();
        track.setArtistId(artist == null ? null : artist.id());
        return trackRepository.save(track).doOnNext(saved -> saved.setArtist(artist));
    }

    @Override
    public Mono<Track> findById(String id) {
        return withArtists(trackRepository.findById(id).flux()).next();
    }

    @Override
    public Mono<Void> delete(Track track) {
        return trackRepository.delete(track);
    }

    /** Loads the Artists of all the tracks with one query, rather than one per track. */
    private Flux<Track> withArtists(Flux<Track> found) {
        return found.collectList().flatMapMany(list -> {
            var ids = list.stream().map(Track::getArtistId).filter(Objects::nonNull).distinct().toList();
            return artists.findAllById(ids)
                    .collectMap(Artist::id, Function.identity())
                    .flatMapMany(byId -> Flux.fromIterable(list).doOnNext(track ->
                            track.setArtist(track.getArtistId() == null ? null : byId.get(track.getArtistId()))));
        });
    }
}
