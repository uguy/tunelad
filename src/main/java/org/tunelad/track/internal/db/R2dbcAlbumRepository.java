package org.tunelad.track.internal.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.tunelad.track.internal.Album;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface R2dbcAlbumRepository extends ReactiveCrudRepository<Album, String> {

	Flux<Album> findAllByOrderByTitleAsc();

	Flux<Album> findAllByArtistIdAndTitleIgnoreCaseOrderById(String artistId, String title);
}
