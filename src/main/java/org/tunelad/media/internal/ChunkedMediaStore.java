package org.tunelad.media.internal;

import io.r2dbc.spi.Readable;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.media.MediaStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps audio in PostgreSQL, one {@code media_files} row per key with its audio in {@code media_chunks}, along with
 * its waveform: storing audio leaves a pending waveform and announces it with {@link AudioStored}, so
 * {@link WaveformComputer} computes it after the fact.
 */
@Component
public class ChunkedMediaStore implements MediaStore {

    /** The size of a chunk, except the last one of a file. */
    static final int CHUNK_SIZE = 255 * 1024;

    private final DatabaseClient client;
    private final Waveforms waveforms;
    private final Renditions renditions;
    private final TransactionalEvents events;
    private final TransactionalOperator transactionalOperator;

    ChunkedMediaStore(DatabaseClient client,
                      Waveforms waveforms,
                      Renditions renditions,
                      TransactionalEvents events,
                      @Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
        this.client = client;
        this.waveforms = waveforms;
        this.renditions = renditions;
        this.events = events;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Stores the file and its chunks in one transaction, so audio that fails half way leaves nothing behind. Storing
     * under a key that is already used replaces the audio.
     */
    @Override
    public Mono<Void> store(String key, Publisher<DataBuffer> audio, String contentType) {
        return write(key, audio, contentType).then(Mono.defer(() -> awaitWaveform(key, contentType)));
    }

    /**
     * Stores the rendition of the audio stored under the key: no waveform and no event, since it is derived from
     * audio that has them. It lives and goes with that audio.
     */
    Mono<Void> storeRendition(String key, Publisher<DataBuffer> rendition) {
        return write(Playable.renditionKey(key), rendition, Playable.RENDITION_CONTENT_TYPE);
    }

    private Mono<Void> write(String key, Publisher<DataBuffer> audio, String contentType) {
        return Mono.defer(() -> {
            var type = contentType(contentType);
            return delete(key)
                    .then(client.sql("""
                            INSERT INTO media_files (file_key, length, chunk_size, content_type)
                            VALUES (:key, 0, :chunkSize, :contentType)""")
                            .bind("key", key).bind("chunkSize", CHUNK_SIZE).bind("contentType", type)
                            .then())
                    .then(chunks(Flux.from(audio))
                            .index()
                            .concatMap(chunk -> insertChunk(key, chunk.getT1().intValue(), chunk.getT2())
                                    .thenReturn((long) chunk.getT2().length))
                            .reduce(0L, Long::sum))
                    .flatMap(length -> client.sql("UPDATE media_files SET length = :length WHERE file_key = :key")
                            .bind("length", length).bind("key", key)
                            .then());
        }).as(transactionalOperator::transactional);
    }

    private Mono<Void> insertChunk(String key, int n, byte[] data) {
        return client.sql("INSERT INTO media_chunks (file_key, n, data) VALUES (:key, :n, :data)")
                .bind("key", key).bind("n", n).bind("data", ByteBuffer.wrap(data))
                .then();
    }

    /** Re-cuts the audio, whatever the size of its buffers, in chunks of {@link #CHUNK_SIZE} bytes. */
    private static Flux<byte[]> chunks(Flux<DataBuffer> audio) {
        return Flux.defer(() -> {
            var pending = new ByteArrayOutputStream(CHUNK_SIZE);
            return audio.concatMap(buffer -> {
                var bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                var full = new ArrayList<byte[]>();
                int offset = 0;
                while (offset < bytes.length) {
                    int take = Math.min(CHUNK_SIZE - pending.size(), bytes.length - offset);
                    pending.write(bytes, offset, take);
                    offset += take;
                    if (pending.size() == CHUNK_SIZE) {
                        full.add(pending.toByteArray());
                        pending.reset();
                    }
                }
                return Flux.fromIterable(full);
            }).concatWith(Mono.fromSupplier(pending::toByteArray).filter(rest -> rest.length > 0));
        });
    }

    /**
     * Saves a pending waveform, and a pending rendition when browsers cannot play the audio, and announces it, in a transaction of its own: callers may store audio outside any
     * transaction (uploads do), and module listeners only hear about events published within one.
     */
    private Mono<Void> awaitWaveform(String key, String contentType) {
        var rendition = Playable.byBrowsers(contentType) ? Mono.<Void>empty() : renditions.savePending(key);
        return waveforms.savePending(key)
                .then(rendition)
                .then(events.publish(new AudioStored(key)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<DataBuffer> read(String key) {
        return find(key).flatMapMany(media -> media.length() == 0 ? Flux.empty() : media.content(0, media.length() - 1));
    }

    /** Deletes the audio and, with it, its chunks, waveform and rendition. */
    @Override
    public Mono<Void> delete(String key) {
        return client.sql("DELETE FROM media_files WHERE file_key = :key OR file_key = :rendition")
                .bind("key", key).bind("rendition", Playable.renditionKey(key)).then();
    }

    /** The keys of all stored audio, renditions excepted: they are derived from the audio. */
    Flux<String> keys() {
        return client.sql("SELECT file_key FROM media_files").map(row -> row.get("file_key", String.class)).all()
                .filter(key -> !Playable.isRenditionKey(key));
    }

    /** The audio stored under that key, or empty when there is none. */
    public Mono<Media> find(String key) {
        return client.sql("SELECT length, chunk_size, content_type FROM media_files WHERE file_key = :key")
                .bind("key", key)
                .map(row -> (Media) new ChunkedMedia(key, row.get("length", Long.class),
                        row.get("chunk_size", Integer.class), contentType(row.get("content_type", String.class))))
                .one();
    }

    /** The rendition of the audio stored under that key, or empty when it has none (yet). */
    public Mono<Media> findRendition(String key) {
        return find(Playable.renditionKey(key));
    }

    private static String contentType(String stored) {
        return stored == null || stored.isBlank() ? Media.DEFAULT_CONTENT_TYPE : stored;
    }

    /** Reads a byte slice by querying only the chunks that cover it, then trimming the first and last of them. */
    private class ChunkedMedia implements Media {

        private final String key;
        private final long length;
        private final int chunkSize;
        private final String contentType;

        ChunkedMedia(String key, long length, int chunkSize, String contentType) {
            this.key = key;
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
            return client.sql("""
                            SELECT n, data FROM media_chunks
                            WHERE file_key = :key AND n BETWEEN :from AND :to ORDER BY n""")
                    .bind("key", key)
                    .bind("from", (int) (first / chunkSize))
                    .bind("to", (int) (last / chunkSize))
                    .map(chunk -> slice(chunk, first, last))
                    .all();
        }

        /** The part of a chunk that falls within [first, last]. */
        private DataBuffer slice(Readable chunk, long first, long last) {
            long chunkStart = (long) chunk.get("n", Integer.class) * chunkSize;
            byte[] data = chunk.get("data", byte[].class);
            int from = (int) (Math.max(first, chunkStart) - chunkStart);
            int to = (int) (Math.min(last, chunkStart + data.length - 1) - chunkStart);
            return DefaultDataBufferFactory.sharedInstance.wrap(ByteBuffer.wrap(data, from, to - from + 1));
        }
    }
}
