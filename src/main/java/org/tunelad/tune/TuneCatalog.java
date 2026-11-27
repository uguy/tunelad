package org.tunelad.tune;

import reactor.core.publisher.Flux;

import java.util.Collection;

/** What other modules may ask of the tunes: look some up by id. */
public interface TuneCatalog {

	/** The tunes with these ids; ids that match none are ignored. */
	Flux<TuneDTO> findAllById(Collection<String> ids);
}
