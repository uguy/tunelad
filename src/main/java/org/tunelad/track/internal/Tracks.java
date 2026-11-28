package org.tunelad.track.internal;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Tracks {
	Flux<Track> findAll();

	Mono<Track> save(Track track);

	Mono<Track> findById(String id);

	/** The tracks of the album, ordered by position. */
	Flux<Track> findByAlbumId(String albumId);

	/** Like {@link #findById}, locking the track's row until the transaction ends. Needs a transaction. */
	Mono<Track> findByIdForUpdate(String id);

	/** Replaces the track's Performances and nothing else: the track's own row, and its version, are not touched. */
	Mono<Void> savePerformances(Track track);

	/** Every Performance of the Tune across Tracks, ordered by Track title. */
	Flux<org.tunelad.track.TunePerformanceDTO> findPerformancesByTune(String tuneId);

	/** The tracks with these ids; ids that match no track are ignored, and the order is unspecified. */
	Flux<Track> findAllById(java.util.Collection<String> ids);

	Mono<Void> delete(Track track);
}
