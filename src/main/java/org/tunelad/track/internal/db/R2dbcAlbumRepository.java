package org.tunelad.track.internal.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.tunelad.track.internal.Album;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface R2dbcAlbumRepository extends ReactiveCrudRepository<Album, String> {

	Flux<Album> findAllByOrderByTitleAsc();

	@Query("""
			SELECT DISTINCT a.* FROM track_albums a
				JOIN track_tracks t ON t.album_id = a.id
				JOIN track_performances p ON p.track_id = t.id
			WHERE p.tune_id = :tuneId
			ORDER BY a.title, a.id""")
	Flux<Album> findAllByTune(String tuneId);

	Flux<Album> findAllByArtistIdAndTitleIgnoreCaseOrderById(String artistId, String title);
}
