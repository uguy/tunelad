package org.tunelad.track.internal.db;

import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsOperations;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsResource;
import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class DbTracks implements Tracks {

    private final MongoTrackRepository mongoTrackRepository;

    private final ReactiveMongoOperations mongoOperations;
    private final ReactiveGridFsOperations gridFsOperations;

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
    public Mono<Track> saveMedia(Track track, Publisher<DataBuffer> data) {
        return gridFsOperations.store(data, track.getId()).then(Mono.just(track));
    }

    @Override
    public Mono<Track> findById(String id) {
        return mongoTrackRepository.findById(id);
    }

    @Override
    public Mono<Void> deleteAll() {
        return mongoTrackRepository.deleteAll()
                .then(gridFsOperations.delete(Query.query(Criteria.where("filename").exists(true))));
    }

    @Override
    public Flux<DataBuffer> readMedia(String id) {
        return gridFsOperations.getResources(id)
                .flatMap(ReactiveGridFsResource::getContent);
    }

    @Override
    public Flux<Track> searchTracks(String q) {
        if (q.isBlank()) return mongoTrackRepository.findAll();
        TextQuery query = TextQuery.queryText(TextCriteria.forDefaultLanguage().caseSensitive(false).matching(q)).sortByScore();
        return mongoOperations.find(query, Track.class);
    }
}
