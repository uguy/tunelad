package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Albums {

	/** Stores the album, with its Artist, which must be stored already. */
	Mono<Album> save(Album album);

	Mono<Album> findById(String id);

	/** The album of this Artist with this title, ignoring case; empty when there is none. */
	Mono<Album> findByArtistAndTitle(String artistId, String title);

	/** Every album, ordered by title. */
	Flux<Album> findAll();
}
