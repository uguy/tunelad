package org.tunelad.track.internal.db;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.track.internal.Artist;
import org.tunelad.track.internal.Artists;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@Component
class DbArtists implements Artists {

    private final DatabaseClient client;

    DbArtists(DatabaseClient client) {
        this.client = client;
    }

    /**
     * Insert-then-select, so that two uploads naming a new artist at once end up on the same row: the unique index
     * on lower(name) makes the loser's insert do nothing.
     */
    @Override
    public Mono<Artist> findOrCreate(String name) {
        if (name == null || name.isBlank()) {
            return Mono.empty();
        }
        var trimmed = name.trim();
        return client.sql("INSERT INTO track_artists (id, name) VALUES (:id, :name) ON CONFLICT (lower(name)) DO NOTHING")
                .bind("id", UUID.randomUUID().toString())
                .bind("name", trimmed)
                .then()
                .then(client.sql("SELECT id, name FROM track_artists WHERE lower(name) = lower(:name)")
                        .bind("name", trimmed)
                        .map(row -> new Artist(row.get("id", String.class), row.get("name", String.class)))
                        .one());
    }

    @Override
    public Flux<Artist> findAllById(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        return client.sql("SELECT id, name FROM track_artists WHERE id IN (:ids)")
                .bind("ids", ids)
                .map(row -> new Artist(row.get("id", String.class), row.get("name", String.class)))
                .all();
    }
}
