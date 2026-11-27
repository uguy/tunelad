package org.tunelad.tune;

import org.tunelad.account.AccountRef;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/** What other modules may ask of the tunes: look some up by id. */
public interface TuneCatalog {

	/** The tunes with these ids; ids that match none are ignored. */
	Flux<TuneDTO> findAllById(Collection<String> ids);

	/**
	 * The Tune with this thesession.org id: the catalogue's, else imported for the owner like an Account's import.
	 * Empty, never an error, when thesession.org has no such Tune or cannot be reached.
	 */
	Mono<TuneDTO> findOrImport(AccountRef owner, int sourceId);
}
