package org.tunelad.search.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;

/** Queries the Albums of the search read model. */
@Component
@RequiredArgsConstructor
public class AlbumSearch {

    private final SearchAlbums searchAlbums;

    /** Albums with at least one word of the query in their title or Artist, best matches first; every album when blank. */
    public Flux<SearchAlbum> search(String q) {
        if (q.isBlank()) return searchAlbums.findAll();
        var words = Arrays.stream(q.trim().split("\\s+")).filter(word -> !word.equalsIgnoreCase("or")).toList();
        if (words.isEmpty()) return Flux.empty();
        return searchAlbums.findByWords(words);
    }
}
