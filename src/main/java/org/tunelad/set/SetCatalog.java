package org.tunelad.set;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/** What other modules may ask of the Sets: look some up by id, with their Tunes. */
public interface SetCatalog {

	/** The Set with this id; empty when there is none. */
	Mono<SetDTO> find(String id);

	/** The Sets with these ids; ids that match none are ignored. */
	Flux<SetDTO> findAllById(Collection<String> ids);
}
