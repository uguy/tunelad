package org.tunelad.media.internal;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.MediaStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps audio in GridFS, one file per key, named after the key, along with its waveform: storing audio leaves a
 * pending waveform and announces it with {@link AudioStored}, so {@link WaveformComputer} computes it after the fact.
 */
@Component
public class GridFsMediaStore implements MediaStore {

    private static final String CONTENT_TYPE_KEY = "_contentType";

    private final ReactiveMongoOperations mongoOperations;
    private final ReactiveGridFsOperations gridFsOperations;
    private final Waveforms waveforms;
    private final TransactionalEvents events;
    private final TransactionalOperator transactionalOperator;

    /** GridFS stores file content in "{bucket}.chunks", one document per chunk: {files_id, n, data}. */
    private final String chunksCollection;

    GridFsMediaStore(ReactiveMongoOperations mongoOperations,
                     ReactiveGridFsOperations gridFsOperations,
                     Waveforms waveforms,
                     TransactionalEvents events,
                     @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager,
                     @Value("${spring.data.mongodb.gridfs.bucket:fs}") String bucket) {
        this.mongoOperations = mongoOperations;
        this.gridFsOperations = gridFsOperations;
        this.waveforms = waveforms;
        this.events = events;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.chunksCollection = bucket + ".chunks";
    }

    /**
     * When the audio fails half way, its error is held back: the driver writes chunks concurrently, and when its source
     * fails it deletes them while inserts it already sent may still land, leaving orphan chunks. Ending the audio
     * there instead lets it finish a truncated file, which is then deleted whole before the error is raised.
     */
    @Override
    public Mono<Void> store(String key, Publisher<DataBuffer> audio, String contentType) {
        return Mono.defer(() -> {
            var failure = new AtomicReference<Throwable>();
            Flux<DataBuffer> untilFailure = Flux.from(audio).onErrorResume(error -> {
                failure.set(error);
                return Mono.empty();
            });
            return gridFsOperations.store(untilFailure, key, contentType)
                    .then(Mono.defer(() -> failure.get() == null
                            ? awaitWaveform(key)
                            : deleteAudio(key).then(Mono.error(failure.get()))));
        });
    }

    /**
     * Saves a pending waveform and announces it, in a transaction of its own: callers may store audio outside any
     * transaction (uploads do), and module listeners only hear about events published within one.
     */
    private Mono<Void> awaitWaveform(String key) {
        return waveforms.save(Waveform.pending(key))
                .then(events.publish(new AudioStored(key)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> delete(String key) {
        return deleteAudio(key).then(waveforms.deleteById(key));
    }

    private Mono<Void> deleteAudio(String key) {
        return gridFsOperations.delete(Query.query(Criteria.where("filename").is(key)));
    }

    /** The audio stored under that key, or empty when there is none. */
    public Mono<Media> find(String key) {
        return gridFsOperations.findOne(Query.query(Criteria.where("filename").is(key)))
                .map(file -> new GridFsMedia(file.getObjectId(), file.getLength(), file.getChunkSize(),
                        contentType(file)));
    }

    /** Spring Data keeps the content type given to {@code store} in the file's metadata, under "_contentType". */
    private static String contentType(GridFSFile file) {
        var metadata = file.getMetadata();
        var contentType = metadata == null ? null : metadata.getString(CONTENT_TYPE_KEY);
        return contentType == null || contentType.isBlank() ? Media.DEFAULT_CONTENT_TYPE : contentType;
    }

    /**
     * Reads a byte slice by querying only the chunks that cover it, then trimming the first and
     * last of them.
     */
    private class GridFsMedia implements Media {

        private final ObjectId fileId;
        private final long length;
        private final int chunkSize;
        private final String contentType;

        GridFsMedia(ObjectId fileId, long length, int chunkSize, String contentType) {
            this.fileId = fileId;
            this.length = length;
            this.chunkSize = chunkSize;
            this.contentType = contentType;
        }

        @Override
        public String contentType() {
            return contentType;
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
