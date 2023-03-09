package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Tracks {
	Flux<Track> findAll();

	Mono<Track> save(Track track);

	Mono<Track> findById(String id);

	Mono<Void> deleteAll();
}
