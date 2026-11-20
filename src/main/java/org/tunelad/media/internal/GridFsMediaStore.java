package org.tunelad.media.internal;

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
import org.tunelad.media.MediaStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/** Keeps audio in GridFS, one file per key, named after the key. */
@Component
public class GridFsMediaStore implements MediaStore {

    private final ReactiveMongoOperations mongoOperations;
    private final ReactiveGridFsOperations gridFsOperations;

    /** GridFS stores file content in "{bucket}.chunks", one document per chunk: {files_id, n, data}. */
    private final String chunksCollection;

    GridFsMediaStore(ReactiveMongoOperations mongoOperations,
                     ReactiveGridFsOperations gridFsOperations,
                     @Value("${spring.data.mongodb.gridfs.bucket:fs}") String bucket) {
        this.mongoOperations = mongoOperations;
        this.gridFsOperations = gridFsOperations;
        this.chunksCollection = bucket + ".chunks";
    }

    @Override
    public Mono<Void> store(String key, Publisher<DataBuffer> audio) {
        return gridFsOperations.store(audio, key).then();
    }

    @Override
    public Mono<Void> delete(String key) {
        return gridFsOperations.delete(Query.query(Criteria.where("filename").is(key)));
    }

    /** The audio stored under that key, or empty when there is none. */
    public Mono<Media> find(String key) {
        return gridFsOperations.findOne(Query.query(Criteria.where("filename").is(key)))
                .map(file -> new GridFsMedia(file.getObjectId(), file.getLength(), file.getChunkSize()));
    }

    /**
     * Reads a byte slice by querying only the chunks that cover it, then trimming the first and
     * last of them.
     */
    private class GridFsMedia implements Media {

        private final ObjectId fileId;
        private final long length;
        private final int chunkSize;

        GridFsMedia(ObjectId fileId, long length, int chunkSize) {
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
