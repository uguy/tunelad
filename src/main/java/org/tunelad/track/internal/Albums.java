package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Albums {

	/** Stores the album, with its Artist, which must be stored already. */
	Mono<Album> save(Album album);

	Mono<Album> findById(String id);

	/** The album of this Artist with this title, ignoring case; empty when there is none. */
	Mono<Album> findByArtistAndTitle(String artistId, String title);

	/** The albums with a Track that has a resolved Performance of the Tune, ordered by title. */
	Flux<Album> findAllByTune(String tuneId);

	/** Every album, ordered by title. */
	Flux<Album> findAll();
}
