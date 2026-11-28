package org.tunelad.tune;

import org.tunelad.account.AccountRef;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

/** What other modules may ask of the tunes: look some up by id. */
public interface TuneCatalog {

	/** The tunes with these ids; ids that match none are ignored. */
	Flux<TuneDTO> findAllById(Collection<String> ids);

	/** The catalogue's Tunes by thesession.org id, for the ids it has; never creates one. One query, for bulk imports. */
	Mono<Map<Integer, TuneDTO>> findBySourceIds(Collection<Integer> sourceIds);

	/**
	 * The Tune with this thesession.org id: the catalogue's, else imported for the owner like an Account's import.
	 * Empty, never an error, when thesession.org has no such Tune or cannot be reached.
	 */
	Mono<TuneDTO> findOrImport(AccountRef owner, int sourceId);
}
