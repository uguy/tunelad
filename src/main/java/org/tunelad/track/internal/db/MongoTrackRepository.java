package org.tunelad.track.internal.db;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.tunelad.track.internal.Track;

public interface MongoTrackRepository extends ReactiveMongoRepository<Track, String> {

}
