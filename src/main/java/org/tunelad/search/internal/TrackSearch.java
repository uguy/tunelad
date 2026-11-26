package org.tunelad.search.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;

/** Queries the search read model. */
@Component
@RequiredArgsConstructor
public class TrackSearch {

    private final SearchTracks searchTracks;

    /**
     * Tracks with at least one word of the query in their title or description, best matches first; every track
     * when the query is blank.
     */
    public Flux<SearchTrack> search(String q) {
        if (q.isBlank()) return searchTracks.findAll();
        // Words are matched whole, so the query's operators and punctuation are left to the parser to ignore
        var words = Arrays.stream(q.trim().split("\\s+"))
                .filter(word -> !word.equalsIgnoreCase("or"))
                .toList();
        if (words.isEmpty()) return Flux.empty();
        return searchTracks.findByWords(words);
    }
}
