package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Tracks {
	Flux<Track> findAll();

	Mono<Track> save(Track track);

	Mono<Track> findById(String id);

	/** The tracks with these ids; ids that match no track are ignored, and the order is unspecified. */
	Flux<Track> findAllById(java.util.Collection<String> ids);

	Mono<Void> delete(Track track);
}
