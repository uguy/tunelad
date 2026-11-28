package org.tunelad.track.internal.db;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import io.r2dbc.spi.Readable;
import org.tunelad.track.ArtistKind;
import org.tunelad.track.internal.Artist;
import org.tunelad.track.internal.Artists;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
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
                .then(client.sql("SELECT id, name, kind FROM track_artists WHERE lower(name) = lower(:name)")
                        .bind("name", trimmed)
                        .map(row -> toArtist(row))
                        .one());
    }

    @Override
    public Flux<Artist> findAllById(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        return client.sql("SELECT id, name, kind FROM track_artists WHERE id IN (:ids)")
                .bind("ids", ids)
                .map(row -> toArtist(row))
                .all();
    }

    private static Artist toArtist(Readable row) {
        return new Artist(row.get("id", String.class), row.get("name", String.class),
                ArtistKind.fromColumn(row.get("kind", String.class)));
    }

    @Override
    public Mono<Artist> find(String id) {
        return client.sql("SELECT id, name, kind FROM track_artists WHERE id = :id")
                .bind("id", id)
                .map(DbArtists::toArtist)
                .one();
    }

    @Override
    public Mono<Void> setKind(String id, ArtistKind kind) {
        var spec = client.sql("UPDATE track_artists SET kind = :kind WHERE id = :id").bind("id", id);
        return (kind == null ? spec.bindNull("kind", String.class) : spec.bind("kind", kind.column())).then();
    }

    @Override
    public Flux<Artist> members(String bandId) {
        return client.sql("""
                        SELECT a.id, a.name, a.kind FROM track_band_members m
                                 JOIN track_artists a ON a.id = m.musician_id
                        WHERE m.band_id = :id ORDER BY lower(a.name), a.id""")
                .bind("id", bandId)
                .map(DbArtists::toArtist)
                .all();
    }

    @Override
    public Flux<Artist> bands(String musicianId) {
        return client.sql("""
                        SELECT a.id, a.name, a.kind FROM track_band_members m
                                 JOIN track_artists a ON a.id = m.band_id
                        WHERE m.musician_id = :id ORDER BY lower(a.name), a.id""")
                .bind("id", musicianId)
                .map(DbArtists::toArtist)
                .all();
    }

    @Override
    public Mono<Void> replaceMembers(String bandId, List<String> memberIds) {
        var delete = client.sql("DELETE FROM track_band_members WHERE band_id = :id").bind("id", bandId).then();
        if (memberIds.isEmpty()) {
            return delete;
        }
        var insert = Flux.fromIterable(memberIds)
                .concatMap(member -> client.sql("INSERT INTO track_band_members (band_id, musician_id) VALUES (:band, :member)")
                        .bind("band", bandId).bind("member", member).then())
                .then();
        var classify = client.sql("UPDATE track_artists SET kind = 'musician' WHERE id IN (:ids)")
                .bind("ids", memberIds).then();
        return delete.then(insert).then(classify);
    }
}
