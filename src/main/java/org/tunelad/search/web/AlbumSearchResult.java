package org.tunelad.search.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.search.internal.SearchAlbum;

import java.time.LocalDate;

@Schema(description = "An album matching a search")
public record AlbumSearchResult(
        String id,
        String title,
        String artist,
        @Schema(nullable = true) String coverUrl,
        @Schema(nullable = true) LocalDate releaseDate) {

    static AlbumSearchResult of(SearchAlbum album) {
        return new AlbumSearchResult(album.id(), album.title(), album.artist(), album.coverUrl(), album.releaseDate());
    }
}
