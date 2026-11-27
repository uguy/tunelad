package org.tunelad.track.internal.db;

import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
class DbTracks implements Tracks {

    private final R2dbcTrackRepository trackRepository;

    DbTracks(R2dbcTrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }

    @Override
    public Flux<Track> findAll() {
        return trackRepository.findAll();
    }

    @Override
    public Flux<Track> findAllById(java.util.Collection<String> ids) {
        return ids.isEmpty() ? Flux.empty() : trackRepository.findAllById(ids);
    }

    @Override
    public Mono<Track> save(Track track) {
        if (track.getId() == null || track.getId().isBlank()) {
            track.setId(UUID.randomUUID().toString());
        }
        return trackRepository.save(track);
    }

    @Override
    public Mono<Track> findById(String id) {
        return trackRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(Track track) {
        return trackRepository.delete(track);
    }
}
