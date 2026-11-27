package org.tunelad.media.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** The state of the renditions of stored audio, by the original audio's key. They go away with their audio. */
@Component
public class Renditions {

    private final DatabaseClient client;

    Renditions(DatabaseClient client) {
        this.client = client;
    }

    /** The state of the audio's rendition; empty when the audio needs none (it is playable as it is). */
    public Mono<RenditionStatus> findStatus(String key) {
        return client.sql("SELECT status FROM media_renditions WHERE file_key = :key")
                .bind("key", key)
                .map(row -> RenditionStatus.valueOf(row.get("status", String.class)))
                .one();
    }

    /** Saves a pending rendition, replacing the state the key may have. */
    Mono<Void> savePending(String key) {
        return client.sql("""
                        INSERT INTO media_renditions (file_key, status, computed_at) VALUES (:key, 'PENDING', NULL)
                        ON CONFLICT (file_key) DO UPDATE SET status = 'PENDING', computed_at = NULL""")
                .bind("key", key)
                .then();
    }

    /**
     * Records the outcome of the conversion; does nothing when the key has no rendition, such as after its audio
     * went. Emits how many it updated, so that callers announce only what they saved.
     */
    Mono<Long> complete(String key, RenditionStatus status) {
        return client.sql("UPDATE media_renditions SET status = :status, computed_at = :computedAt WHERE file_key = :key")
                .bind("status", status.name())
                .bind("computedAt", Instant.now())
                .bind("key", key)
                .fetch().rowsUpdated();
    }

    /** The keys whose rendition still waits to be made. */
    Flux<String> pendingKeys() {
        return client.sql("SELECT file_key FROM media_renditions WHERE status = 'PENDING'")
                .map(row -> row.get("file_key", String.class))
                .all();
    }
}
