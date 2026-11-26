package org.tunelad.media.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** The waveforms of stored audio, by the audio's key. They go away with their audio. */
@Component
public class Waveforms {

    private final DatabaseClient client;

    Waveforms(DatabaseClient client) {
        this.client = client;
    }

    public Mono<Waveform> findById(String key) {
        return client.sql("SELECT file_key, status, peaks, computed_at FROM media_waveforms WHERE file_key = :key")
                .bind("key", key)
                .map(row -> {
                    var peaks = row.get("peaks", Double[].class);
                    return new Waveform(row.get("file_key", String.class),
                            WaveformStatus.valueOf(row.get("status", String.class)),
                            peaks == null ? List.of() : Arrays.asList(peaks),
                            row.get("computed_at", Instant.class));
                })
                .one();
    }

    /** Saves a pending waveform, replacing the one the key may have. */
    Mono<Void> savePending(String key) {
        return client.sql("""
                        INSERT INTO media_waveforms (file_key, status, peaks, computed_at)
                        VALUES (:key, 'PENDING', '{}', NULL)
                        ON CONFLICT (file_key) DO UPDATE SET status = 'PENDING', peaks = '{}', computed_at = NULL""")
                .bind("key", key)
                .then();
    }

    /** Saves a pending waveform unless the key has one already. */
    Mono<Void> ensurePending(String key) {
        return client.sql("""
                        INSERT INTO media_waveforms (file_key, status, peaks) VALUES (:key, 'PENDING', '{}')
                        ON CONFLICT (file_key) DO NOTHING""")
                .bind("key", key)
                .then();
    }

    /**
     * Records the outcome of the computation; does nothing when the key has no waveform, such as after its audio went.
     * Emits how many waveforms it updated, so that callers announce only what they saved.
     */
    Mono<Long> complete(String key, WaveformStatus status, List<Double> peaks) {
        return client.sql("""
                        UPDATE media_waveforms SET status = :status, peaks = :peaks, computed_at = :computedAt
                        WHERE file_key = :key""")
                .bind("status", status.name())
                .bind("peaks", peaks.toArray(Double[]::new))
                .bind("computedAt", Instant.now())
                .bind("key", key)
                .fetch().rowsUpdated();
    }

    /** The keys whose waveform is computed already, or failed to be. */
    Flux<String> computedKeys() {
        return client.sql("SELECT file_key FROM media_waveforms WHERE status IN ('READY', 'FAILED')")
                .map(row -> row.get("file_key", String.class))
                .all();
    }
}
