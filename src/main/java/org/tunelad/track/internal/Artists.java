package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface Artists {

	/**
	 * The Artist with this name, ignoring case and surrounding whitespace; created on first use, with the name
	 * trimmed. Empty for a blank name.
	 */
	Mono<Artist> findOrCreate(String name);

	/** The artists with these ids; ids that match none are ignored. */
	Flux<Artist> findAllById(Collection<String> ids);
}
