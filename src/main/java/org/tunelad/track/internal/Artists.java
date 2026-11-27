package org.tunelad.track.internal;

import org.tunelad.track.ArtistKind;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface Artists {

	/**
	 * The Artist with this name, ignoring case and surrounding whitespace; created on first use, with the name
	 * trimmed. Empty for a blank name.
	 */
	Mono<Artist> findOrCreate(String name);

	/** The Artist with this id; empty when there is none. */
	Mono<Artist> find(String id);

	/** The artists with these ids; ids that match none are ignored. */
	Flux<Artist> findAllById(Collection<String> ids);

	/** Sets the kind of the Artist (null unclassifies it). */
	Mono<Void> setKind(String id, ArtistKind kind);

	/** The members of a Band, ordered by name. */
	Flux<Artist> members(String bandId);

	/** The Bands a Musician belongs to, ordered by name. */
	Flux<Artist> bands(String musicianId);

	/** Replaces the members of a Band and makes each of them a Musician. */
	Mono<Void> replaceMembers(String bandId, List<String> memberIds);
}
