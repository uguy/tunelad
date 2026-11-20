package org.tunelad.track.internal.db;

import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
class DbTracks implements Tracks {

    private final MongoTrackRepository mongoTrackRepository;

    DbTracks(MongoTrackRepository mongoTrackRepository) {
        this.mongoTrackRepository = mongoTrackRepository;
    }

    @Override
    public Flux<Track> findAll() {
        return mongoTrackRepository.findAll();
    }

    @Override
    public Mono<Track> save(Track track) {
        if (track.getId() == null || track.getId().isBlank()) {
            track.setId(UUID.randomUUID().toString());
        }
        return mongoTrackRepository.save(track);
    }

    @Override
    public Mono<Track> findById(String id) {
        return mongoTrackRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Track track) {
        return mongoTrackRepository.delete(track);
    }
}
