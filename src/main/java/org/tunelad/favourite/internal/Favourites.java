package org.tunelad.favourite.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The favourites table: which account marked which track. Tracks and accounts belong to other modules and are known
 * by id only, with no foreign key.
 */
@Component
class Favourites {

    private final DatabaseClient client;

    Favourites(DatabaseClient client) {
        this.client = client;
    }

    /** Marks the track; true when it was not marked already. */
    Mono<Boolean> add(String accountId, String trackId) {
        return client.sql("""
                        INSERT INTO favourite_favourites (account_id, track_id) VALUES (:account, :track)
                        ON CONFLICT DO NOTHING""")
                .bind("account", accountId).bind("track", trackId)
                .fetch().rowsUpdated().map(rows -> rows > 0);
    }

    /** Unmarks the track; true when it was marked. */
    Mono<Boolean> remove(String accountId, String trackId) {
        return client.sql("DELETE FROM favourite_favourites WHERE account_id = :account AND track_id = :track")
                .bind("account", accountId).bind("track", trackId)
                .fetch().rowsUpdated().map(rows -> rows > 0);
    }

    /** The ids of the account's favourite tracks, most recently marked first. */
    Flux<String> trackIdsOf(String accountId) {
        return client.sql("""
                        SELECT track_id FROM favourite_favourites WHERE account_id = :account
                        ORDER BY created_at DESC, track_id""")
                .bind("account", accountId)
                .map(row -> row.get("track_id", String.class)).all();
    }

    /** Removes the track from every account's favourites, and says whose they were. */
    Flux<String> deleteByTrack(String trackId) {
        return client.sql("DELETE FROM favourite_favourites WHERE track_id = :track RETURNING account_id")
                .bind("track", trackId)
                .map(row -> row.get("account_id", String.class)).all();
    }
}
