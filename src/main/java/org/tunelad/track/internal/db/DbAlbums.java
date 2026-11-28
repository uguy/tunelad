package org.tunelad.track.internal.db;

import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Album;
import org.tunelad.track.internal.Albums;
import org.tunelad.track.internal.Artist;
import org.tunelad.track.internal.Artists;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Component
class DbAlbums implements Albums {

    private final R2dbcAlbumRepository repository;

    private final Artists artists;

    DbAlbums(R2dbcAlbumRepository repository, Artists artists) {
        this.repository = repository;
        this.artists = artists;
    }

    @Override
    public Mono<Album> save(Album album) {
        if (album.getId() == null || album.getId().isBlank()) {
            album.setId(UUID.randomUUID().toString());
        }
        var artist = album.getArtist();
        album.setArtistId(artist == null ? null : artist.id());
        return repository.save(album).doOnNext(saved -> saved.setArtist(artist));
    }

    @Override
    public Mono<Album> findById(String id) {
        return hydrate(repository.findById(id).flux()).next();
    }

    @Override
    public Mono<Album> findByArtistAndTitle(String artistId, String title) {
        return hydrate(repository.findAllByArtistIdAndTitleIgnoreCaseOrderById(artistId, title)).next();
    }

    @Override
    public Flux<Album> findAll() {
        return hydrate(repository.findAllByOrderByTitleAsc());
    }

    /** Loads the Artists of all the albums with one query. */
    private Flux<Album> hydrate(Flux<Album> found) {
        return found.collectList().flatMapMany(list -> {
            if (list.isEmpty()) {
                return Flux.empty();
            }
            var artistIds = list.stream().map(Album::getArtistId).filter(Objects::nonNull).distinct().toList();
            return artists.findAllById(artistIds).collectMap(Artist::id, Function.identity()).flatMapMany(byId -> {
                list.forEach(album -> album.setArtist(byId.get(album.getArtistId())));
                return Flux.fromIterable(list);
            });
        });
    }
}
