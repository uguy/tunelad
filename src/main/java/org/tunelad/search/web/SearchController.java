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
import org.tunelad.search.internal.TrackSearch;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search tracks by words in title or description")
@RequestMapping(value = "api/tracks", produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json"})
public class SearchController {

    private final TrackSearch trackSearch;

    @GetMapping("/search")
    @Operation(summary = "Search tracks",
            description = "Returns the tracks whose title or description contains at least one of the query's "
                    + "words or their variants, case-insensitively, best matches first; every track when the query "
                    + "is blank. Results are eventually consistent: a track becomes searchable within a few seconds "
                    + "of its upload completing.")
    public Flux<TrackSearchResult> search(
            @Parameter(description = "Words to look for; blank returns every track", example = "reel")
            @RequestParam("q") String q) {
        return trackSearch.search(q).map(TrackSearchResult::of);
    }
}
