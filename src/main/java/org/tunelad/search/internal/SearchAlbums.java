package org.tunelad.search.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.track.AlbumDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/** The search read model of Albums, found by words in their title or Artist. */
@Component
class SearchAlbums {

    private static final String COLUMNS = "id, title, artist, cover_url, release_date";

    private final DatabaseClient client;

    SearchAlbums(DatabaseClient client) {
        this.client = client;
    }

    /** Stores the album, replacing any copy with the same id. */
    Mono<Void> save(AlbumDTO album) {
        var spec = client.sql("""
                INSERT INTO search_albums (id, title, artist, cover_url, release_date)
                VALUES (:id, :title, :artist, :coverUrl, :releaseDate)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, artist = EXCLUDED.artist,
                    cover_url = EXCLUDED.cover_url, release_date = EXCLUDED.release_date""")
                .bind("id", album.id()).bind("title", album.title());
        spec = album.artist() == null ? spec.bindNull("artist", String.class) : spec.bind("artist", album.artist());
        spec = album.coverUrl() == null ? spec.bindNull("coverUrl", String.class) : spec.bind("coverUrl", album.coverUrl());
        spec = album.releaseDate() == null ? spec.bindNull("releaseDate", LocalDate.class)
                : spec.bind("releaseDate", album.releaseDate());
        return spec.then();
    }

    Mono<SearchAlbum> findById(String id) {
        return client.sql("SELECT " + COLUMNS + " FROM search_albums WHERE id = :id").bind("id", id)
                .map(SearchAlbums::toAlbum).one();
    }

    Mono<Void> deleteAll() {
        return client.sql("DELETE FROM search_albums").then();
    }

    Flux<SearchAlbum> findAll() {
        return client.sql("SELECT " + COLUMNS + " FROM search_albums ORDER BY lower(title), id")
                .map(SearchAlbums::toAlbum).all();
    }

    /** The albums matching any of the words (or their variants), best matches first. */
    Flux<SearchAlbum> findByWords(List<String> words) {
        return client.sql("SELECT " + COLUMNS + """
                        , ts_rank(search_vector, query) AS rank
                        FROM search_albums, websearch_to_tsquery('english', :query) AS query
                        WHERE search_vector @@ query
                        ORDER BY rank DESC, id""")
                .bind("query", String.join(" or ", words))
                .map(SearchAlbums::toAlbum)
                .all();
    }

    private static SearchAlbum toAlbum(io.r2dbc.spi.Readable row) {
        return new SearchAlbum(row.get("id", String.class), row.get("title", String.class),
                row.get("artist", String.class), row.get("cover_url", String.class),
                row.get("release_date", LocalDate.class));
    }
}
