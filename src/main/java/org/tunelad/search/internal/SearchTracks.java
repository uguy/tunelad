package org.tunelad.search.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** The search read model: tracks as {@link SearchTrack}s, found by words in their title or description. */
@Component
class SearchTracks {

    private static final String COLUMNS = "id, title, description, artist, album, format, owner_id, owner_display_name";

    private final DatabaseClient client;

    SearchTracks(DatabaseClient client) {
        this.client = client;
    }

    /** Stores the track, replacing any copy with the same id. */
    Mono<Void> save(SearchTrack track) {
        var owner = track.owner();
        var spec = client.sql("""
                INSERT INTO search_tracks (id, title, description, artist, album, format, owner_id, owner_display_name)
                VALUES (:id, :title, :description, :artist, :album, :format, :ownerId, :ownerDisplayName)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description,
                    artist = EXCLUDED.artist, album = EXCLUDED.album, format = EXCLUDED.format,
                    owner_id = EXCLUDED.owner_id, owner_display_name = EXCLUDED.owner_display_name""")
                .bind("id", track.id());
        spec = bind(spec, "title", track.title());
        spec = bind(spec, "description", track.description());
        spec = bind(spec, "artist", track.artist());
        spec = bind(spec, "album", track.album());
        spec = bind(spec, "format", track.format() == null ? null : track.format().name());
        spec = bind(spec, "ownerId", owner == null ? null : owner.id());
        spec = bind(spec, "ownerDisplayName", owner == null ? null : owner.displayName());
        return spec.then();
    }

    Mono<Void> saveAll(List<SearchTrack> tracks) {
        return Flux.fromIterable(tracks).concatMap(this::save).then();
    }

    Mono<SearchTrack> findById(String id) {
        return client.sql("SELECT " + COLUMNS + " FROM search_tracks WHERE id = :id").bind("id", id)
                .map(SearchTracks::toTrack).one();
    }

    Mono<Boolean> existsById(String id) {
        return findById(id).hasElement();
    }

    Mono<Long> count() {
        return client.sql("SELECT count(*) AS n FROM search_tracks").map(row -> row.get("n", Long.class)).one();
    }

    Mono<Void> deleteAll() {
        return client.sql("DELETE FROM search_tracks").then();
    }

    /** Idempotent: deleting a copy that is already gone does nothing. */
    Mono<Void> deleteById(String id) {
        return client.sql("DELETE FROM search_tracks WHERE id = :id").bind("id", id).then();
    }

    Flux<SearchTrack> findAll() {
        return client.sql("SELECT " + COLUMNS + " FROM search_tracks").map(SearchTracks::toTrack).all();
    }

    /** The tracks matching any of the words (or their variants), best matches first. */
    Flux<SearchTrack> findByWords(List<String> words) {
        return client.sql("SELECT " + COLUMNS + """
                        , ts_rank(search_vector, query) AS rank
                        FROM search_tracks, websearch_to_tsquery('english', :query) AS query
                        WHERE search_vector @@ query
                        ORDER BY rank DESC, id""")
                .bind("query", String.join(" or ", words))
                .map(SearchTracks::toTrack)
                .all();
    }

    private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, String name,
                                                          String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static SearchTrack toTrack(io.r2dbc.spi.Readable row) {
        var format = row.get("format", String.class);
        var ownerId = row.get("owner_id", String.class);
        var ownerName = row.get("owner_display_name", String.class);
        return new SearchTrack(row.get("id", String.class), row.get("title", String.class),
                row.get("description", String.class), row.get("artist", String.class),
                row.get("album", String.class), format == null ? null : TrackFormat.valueOf(format),
                ownerId == null && ownerName == null ? null : new TrackOwner(ownerId, ownerName));
    }
}
