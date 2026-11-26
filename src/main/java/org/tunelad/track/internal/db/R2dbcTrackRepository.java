package org.tunelad.track.internal.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.tunelad.track.internal.Track;

public interface R2dbcTrackRepository extends ReactiveCrudRepository<Track, String> {

}
