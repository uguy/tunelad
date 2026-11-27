package org.tunelad.set;

import org.tunelad.account.AccountRef;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/** What other modules may ask of the Sets: look some up by id, with their Tunes. */
public interface SetCatalog {

	/** The Set with this id; empty when there is none. */
	Mono<SetDTO> find(String id);

	/** The Sets with these ids; ids that match none are ignored. */
	Flux<SetDTO> findAllById(Collection<String> ids);

	/** The Set of this ordered list of one to twenty Tune ids, created for the Account when none exists yet. */
	Mono<SetDTO> findOrCreate(AccountRef creator, List<String> tuneIds);
}
