package org.tunelad.search.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.search.internal.AlbumSearch;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@Tag(name = "Search")
@RequestMapping(value = "api/albums", produces = MediaType.APPLICATION_JSON_VALUE)
public class AlbumSearchController {

    private final AlbumSearch albumSearch;

    @GetMapping("/search")
    @Operation(summary = "Search albums",
            description = "Returns the albums whose title or artist contains at least one of the query's words or their "
                    + "variants, case-insensitively, best matches first; every album when blank.")
    public Flux<AlbumSearchResult> search(
            @Parameter(description = "Words to look for; blank returns every album", example = "dervish")
            @RequestParam("q") String q) {
        return albumSearch.search(q).map(AlbumSearchResult::of);
    }
}
