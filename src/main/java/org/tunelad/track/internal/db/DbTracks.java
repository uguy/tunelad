package org.tunelad.track.internal.db;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsOperations;
import org.springframework.stereotype.Component;
import org.tunelad.track.TrackMedia;
import org.tunelad.track.internal.Track;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.UUID;

@Component
class DbTracks implements Tracks {

    private final MongoTrackRepository mongoTrackRepository;

    private final ReactiveMongoOperations mongoOperations;
    private final ReactiveGridFsOperations gridFsOperations;

    /** GridFS stores file content in "{bucket}.chunks", one document per chunk: {files_id, n, data}. */
    private final String chunksCollection;

    DbTracks(MongoTrackRepository mongoTrackRepository,
             ReactiveMongoOperations mongoOperations,
             ReactiveGridFsOperations gridFsOperations,
             @Value("${spring.data.mongodb.gridfs.bucket:fs}") String bucket) {
        this.mongoTrackRepository = mongoTrackRepository;
        this.mongoOperations = mongoOperations;
        this.gridFsOperations = gridFsOperations;
        this.chunksCollection = bucket + ".chunks";
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
    public Mono<Track> saveMedia(Track track, Publisher<DataBuffer> data) {
        return gridFsOperations.store(data, track.getId()).then(Mono.just(track));
    }

    @Override
    public Mono<Track> findById(String id) {
        return mongoTrackRepository.findById(id);
    }

    @Override
    public Mono<TrackMedia> readMedia(String id) {
        return gridFsOperations.findOne(Query.query(Criteria.where("filename").is(id)))
                .map(file -> new GridFsTrackMedia(file.getObjectId(), file.getLength(), file.getChunkSize()));
    }

    /**
     * Reads a byte slice by querying only the chunks that cover it, then trimming the first and
     * last of them.
     */
    private class GridFsTrackMedia implements TrackMedia {

        private final ObjectId fileId;
        private final long length;
        private final int chunkSize;

        GridFsTrackMedia(ObjectId fileId, long length, int chunkSize) {
            this.fileId = fileId;
            this.length = length;
            this.chunkSize = chunkSize;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public Flux<DataBuffer> content(long first, long last) {
            if (last < first) return Flux.empty();
            if (first < 0 || last >= length) {
                throw new IllegalArgumentException(
                        "Byte range %d-%d is outside a file of %d bytes".formatted(first, last, length));
            }
            var chunks = Query.query(Criteria.where("files_id").is(fileId)
                            .and("n").gte(first / chunkSize).lte(last / chunkSize))
                    .with(Sort.by("n"));
            return mongoOperations.find(chunks, Document.class, chunksCollection)
                    .map(chunk -> slice(chunk, first, last));
        }

        /** The part of a chunk that falls within [first, last]. */
        private DataBuffer slice(Document chunk, long first, long last) {
            long chunkStart = (long) chunk.getInteger("n") * chunkSize;
            byte[] data = bytes(chunk.get("data"));
            int from = (int) (Math.max(first, chunkStart) - chunkStart);
            int to = (int) (Math.min(last, chunkStart + data.length - 1) - chunkStart);
            return DefaultDataBufferFactory.sharedInstance.wrap(ByteBuffer.wrap(data, from, to - from + 1));
        }

        private static byte[] bytes(Object data) {
            return data instanceof Binary binary ? binary.getData() : (byte[]) data;
        }
    }
}
