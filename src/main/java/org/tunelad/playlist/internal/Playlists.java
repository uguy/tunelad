package org.tunelad.playlist.internal;

import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.playlist.PlaylistSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * The playlist tables: playlists, and the tracks they hold in order. Accounts and tracks belong to other modules and
 * are known by id only. The caller decides who may touch what: every method here trusts the ids it is given.
 */
@Component
class Playlists {

    private static final String SUMMARY = """
            SELECT p.id, p.name, p.description, p.updated_at,
                   (SELECT count(*) FROM playlist_items i WHERE i.playlist_id = p.id) AS track_count
            FROM playlist_playlists p""";

    private final DatabaseClient client;

    Playlists(DatabaseClient client) {
        this.client = client;
    }

    Mono<String> insert(String ownerId, String name, String description) {
        return bind(client.sql("""
                        INSERT INTO playlist_playlists (owner_id, name, description) VALUES (:owner, :name, :description)
                        RETURNING id""")
                .bind("owner", ownerId).bind("name", name), "description", description)
                .map(row -> row.get("id", String.class)).one();
    }

    /** The playlist, when it exists and belongs to that owner. */
    Mono<PlaylistSummary> findOwned(String ownerId, String id) {
        return client.sql(SUMMARY + " WHERE p.id = :id AND p.owner_id = :owner")
                .bind("id", id).bind("owner", ownerId)
                .map(Playlists::toSummary).one();
    }

    /** The owner's playlists, the most recently changed first. */
    Flux<PlaylistSummary> findByOwner(String ownerId) {
        return client.sql(SUMMARY + " WHERE p.owner_id = :owner ORDER BY p.updated_at DESC, p.id")
                .bind("owner", ownerId)
                .map(Playlists::toSummary).all();
    }

    Mono<Void> rename(String id, String name, String description) {
        return bind(client.sql("""
                        UPDATE playlist_playlists SET name = :name, description = :description,
                            updated_at = clock_timestamp() WHERE id = :id""")
                .bind("id", id).bind("name", name), "description", description).then();
    }

    /** Deletes the playlist and, with it, its items; the tracks are not touched. */
    Mono<Void> delete(String id) {
        return client.sql("DELETE FROM playlist_playlists WHERE id = :id").bind("id", id).then();
    }

    Mono<Void> touch(String id) {
        return client.sql("UPDATE playlist_playlists SET updated_at = clock_timestamp() WHERE id = :id")
                .bind("id", id).then();
    }

    Mono<String> ownerOf(String id) {
        return client.sql("SELECT owner_id FROM playlist_playlists WHERE id = :id").bind("id", id)
                .map(row -> row.get("owner_id", String.class)).one();
    }

    /** The ids of the playlist's tracks, in playlist order. */
    Flux<String> trackIds(String playlistId) {
        return client.sql("SELECT track_id FROM playlist_items WHERE playlist_id = :id ORDER BY position")
                .bind("id", playlistId)
                .map(row -> row.get("track_id", String.class)).all();
    }

    /** Appends the track; false when the playlist holds it already. */
    Mono<Boolean> addItem(String playlistId, String trackId) {
        return client.sql("""
                        INSERT INTO playlist_items (playlist_id, track_id, position)
                        SELECT :playlist, :track, coalesce(max(position), -1) + 1
                        FROM playlist_items WHERE playlist_id = :playlist
                        ON CONFLICT (playlist_id, track_id) DO NOTHING""")
                .bind("playlist", playlistId).bind("track", trackId)
                .fetch().rowsUpdated().map(rows -> rows > 0);
    }

    /** Removes the track and closes the gap it leaves; false when the playlist did not hold it. */
    Mono<Boolean> removeItem(String playlistId, String trackId) {
        return client.sql("DELETE FROM playlist_items WHERE playlist_id = :playlist AND track_id = :track")
                .bind("playlist", playlistId).bind("track", trackId)
                .fetch().rowsUpdated().map(rows -> rows > 0)
                .flatMap(removed -> removed ? renumber(playlistId).thenReturn(true) : Mono.just(false));
    }

    Mono<Void> setPosition(String playlistId, String trackId, int position) {
        return client.sql("UPDATE playlist_items SET position = :position WHERE playlist_id = :playlist AND track_id = :track")
                .bind("position", position).bind("playlist", playlistId).bind("track", trackId).then();
    }

    /** Numbers the playlist's items 0, 1, 2 ... in their current order, closing any gap. */
    Mono<Void> renumber(String playlistId) {
        return client.sql("""
                        UPDATE playlist_items i SET position = r.position
                        FROM (SELECT track_id, row_number() OVER (ORDER BY position) - 1 AS position
                              FROM playlist_items WHERE playlist_id = :playlist) r
                        WHERE i.playlist_id = :playlist AND i.track_id = r.track_id""")
                .bind("playlist", playlistId).then();
    }

    /** Removes the track from every playlist, and says which ones held it. */
    Flux<String> deleteItemsByTrack(String trackId) {
        return client.sql("DELETE FROM playlist_items WHERE track_id = :track RETURNING playlist_id")
                .bind("track", trackId)
                .map(row -> row.get("playlist_id", String.class)).all();
    }

    private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, String name,
                                                          String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static PlaylistSummary toSummary(Readable row) {
        var updated = row.get("updated_at", OffsetDateTime.class);
        var count = row.get("track_count", Long.class);
        return new PlaylistSummary(row.get("id", String.class), row.get("name", String.class),
                row.get("description", String.class), count == null ? 0 : count.intValue(),
                updated == null ? null : updated.toInstant());
    }
}
